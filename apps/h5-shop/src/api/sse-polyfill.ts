/**
 * SSE Polyfill — 支持 POST 方法的 SSE
 * EventSource 只支持 GET，而 ai-orchestrator 是 POST，
 * 所以用 fetch + ReadableStream 手写解析。
 */
import { useUserStore } from '@/stores/user';

export interface SseEvent {
  event: string;
  data: any;
}

export type SseCallback = (event: string, data: any) => void;

/**
 * POST 方式发起 SSE 连接
 * @param url 请求 URL
 * @param body POST body
 * @param onMessage 事件回调
 * @param signal 可选的 AbortSignal
 */
export async function postSse(
  url: string,
  body: Record<string, unknown>,
  onMessage: SseCallback,
  signal?: AbortSignal,
): Promise<void> {
  const userStore = useUserStore();

  const resp = await fetch(url, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      Authorization: `Bearer ${userStore.token}`,
    },
    body: JSON.stringify(body),
    signal,
  });

  if (!resp.ok) {
    const errBody = await resp.text();
    onMessage('error', { message: `HTTP ${resp.status}: ${errBody}` });
    onMessage('done', null);
    return;
  }

  const reader = resp.body!.getReader();
  const decoder = new TextDecoder();
  let buffer = '';

  try {
    while (true) {
      const { done, value } = await reader.read();
      if (done) break;

      buffer += decoder.decode(value, { stream: true });
      // 服务端（sse_starlette）默认以 \r\n 分隔，统一规范化为 \n，
      // 否则按 \n\n 分割时永远切不出事件（\r\n\r\n 不含连续 \n\n）
      buffer = buffer.replace(/\r\n/g, '\n');

      // SSE 事件以 \n\n 分隔
      const parts = buffer.split('\n\n');
      buffer = parts.pop() || '';

      for (const part of parts) {
        if (!part.trim()) continue;
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
    if (err.name !== 'AbortError') {
      onMessage('error', { message: err.message });
    }
  }
}