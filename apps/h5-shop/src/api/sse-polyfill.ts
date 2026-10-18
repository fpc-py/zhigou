/**
 * SSE Polyfill — 支持 POST 方法的 SSE（断线重连 + 心跳活性 + 假死超时释放）
 *
 * EventSource 只支持 GET，而 ai-orchestrator 是 POST，
 * 所以用 fetch + ReadableStream 手写解析。
 *
 * 工作流2 SSE 连接管理增强：
 * 1. 断线重连：流中断/网络错误 → 指数退避重连（1s/2s/4s/8s，最多 maxReconnects 次），
 *    用同一 sessionId 重发 query（服务端会话历史在 done 时才写回，重连不产生重复上下文 → 幂等续传）。
 * 2. 心跳活性：服务端 sse_starlette 每 15s 发 `: ping` 注释行，客户端把「读到任何字节」
 *    视为连接存活信号（含注释行），据此重置假死计时。
 * 3. 假死超时释放：idleTimeoutMs 内无任何字节 → 判定连接假死 → abort → 自动重连，
 *    避免页面「转圈圈永远不结束」。
 */
import { useUserStore } from '@/stores/user';

export interface SseEvent {
  event: string;
  data: any;
}

export type SseCallback = (event: string, data: any) => void;

export interface SseOptions {
  signal?: AbortSignal;
  /** 每次重连前回调（UI 可提示「连接恢复中」） */
  onReconnect?: (attempt: number) => void;
  /** 最大重连次数，默认 4（1s+2s+4s+8s） */
  maxReconnects?: number;
  /** 重连基础退避（ms），默认 1000，按 2^attempt 递增 */
  reconnectBaseDelayMs?: number;
  /** 假死判定：无任何字节的闲置时长（ms），默认 30000 */
  idleTimeoutMs?: number;
  /** 是否启用假死超时释放，默认 true */
  idleTimeoutEnabled?: boolean;
}

const sleep = (ms: number) => new Promise<void>((r) => setTimeout(r, ms));

/**
 * POST 方式发起 SSE 连接（带断线重连与假死超时释放）
 * @param url 请求 URL
 * @param body POST body
 * @param onMessage 事件回调
 * @param options 连接管理选项
 */
export async function postSse(
  url: string,
  body: Record<string, unknown>,
  onMessage: SseCallback,
  options: SseOptions = {},
): Promise<void> {
  const {
    signal,
    onReconnect,
    maxReconnects = 4,
    reconnectBaseDelayMs = 1000,
    idleTimeoutMs = 30000,
    idleTimeoutEnabled = true,
  } = options;
  const userStore = useUserStore();

  const attemptOnce = async (attempt: number): Promise<boolean> => {
    // 返回 true=会话已正常结束（done/错误上报完毕）；false=需要重连
    let finished = false;
    let idleTimer: ReturnType<typeof setTimeout> | null = null;
    let abortController: AbortController | null = null;
    const effectiveSignal = signal;

    const startIdleWatch = (reset: () => void) => {
      if (!idleTimeoutEnabled) return;
      if (idleTimer) clearTimeout(idleTimer);
      idleTimer = setTimeout(() => {
        // 假死：idleTimeoutMs 内无任何字节 → 主动释放连接触发重连
        reset();
      }, idleTimeoutMs);
    };

    try {
      const resp = await fetch(url, {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          Authorization: `Bearer ${userStore.token}`,
        },
        body: JSON.stringify(body),
        signal: effectiveSignal,
      });

      if (!resp.ok) {
        const errBody = await resp.text();
        onMessage('error', { message: `HTTP ${resp.status}: ${errBody}` });
        onMessage('done', null);
        return true;
      }

      const reader = resp.body!.getReader();
      const decoder = new TextDecoder();
      let buffer = '';

      while (true) {
        // 每轮读取前重启假死计时：任何字节（含 `: ping` 注释）都是存活信号
        if (idleTimer) clearTimeout(idleTimer);
        const idlePromise = new Promise<null>((resolve) => {
          idleTimer = setTimeout(() => resolve(null), idleTimeoutMs);
        });

        const readPromise = reader.read();
        const result = await Promise.race([readPromise, idlePromise]);

        if (result === null) {
          // 假死超时 → 释放连接（abort 让 reader.read 抛 AbortError）→ 走重连
          try { await reader.cancel(); } catch { /* 忽略 */ }
          finished = false;
          break;
        }

        const { done, value } = result as { done: boolean; value?: Uint8Array };
        if (done) {
          finished = true;
          break;
        }

        buffer += decoder.decode(value, { stream: true });
        // 服务端（sse_starlette）默认以 \r\n 分隔，统一规范化为 \n，
        // 否则按 \n\n 分割时永远切不出事件（\r\n\r\n 不含连续 \n\n）
        buffer = buffer.replace(/\r\n/g, '\n');

        // SSE 事件以 \n\n 分隔
        const parts = buffer.split('\n\n');
        buffer = parts.pop() || '';

        for (const part of parts) {
          if (!part.trim()) continue; // 心跳注释行（: ping）等空事件直接跳过
          const eventMatch = part.match(/^event: (.+)$/m);
          const dataMatch = part.match(/^data: (.+)$/m);

          const eventType = eventMatch?.[1];
          const rawData = dataMatch?.[1];
          if (eventType && rawData != null) {
            try {
              const parsed = JSON.parse(rawData);
              onMessage(eventType, parsed);
            } catch {
              onMessage(eventType, rawData);
            }
          }
        }
      }
    } catch (err: any) {
      if (err?.name === 'AbortError') {
        // 主动 abort（用户取消 / 假死释放）：
        // - 用户取消 → 不再重连
        // - 假死释放 → finished=false，由外层重连
        finished = false;
      } else {
        // 网络错误 / 流中断 → 需要重连
        onMessage('error', { message: err.message });
        finished = false;
      }
    } finally {
      if (idleTimer) clearTimeout(idleTimer);
    }
    return finished;
  };

  for (let attempt = 0; attempt <= maxReconnects; attempt++) {
    if (attempt > 0) {
      onReconnect?.(attempt);
      await sleep(reconnectBaseDelayMs * 2 ** (attempt - 1));
    }
    const finished = await attemptOnce(attempt);
    if (finished) return;
    if (signal?.aborted) return; // 用户主动取消 → 停止重连
  }

  // 重连次数耗尽
  onMessage('error', { message: '连接已断开，多次重连失败，请稍后重试' });
  onMessage('done', null);
}
