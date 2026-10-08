import { postSse } from './sse-polyfill';
import type { SseCallback } from './sse-polyfill';

/**
 * POST SSE 聊天
 * @param query 用户消息
 * @param sessionId 会话 ID
 * @param onMessage 事件回调 (event, data)
 * @param signal 取消信号
 */
export function chatSse(
  query: string,
  sessionId: string,
  onMessage: SseCallback,
  signal?: AbortSignal,
): Promise<void> {
  return postSse(
    '/api/chat/sse',
    { query, userId: '', sessionId }, // userId 由 BFF 从 JWT 提取
    onMessage,
    signal,
  );
}

/** 清空指定会话历史（透传 BFF → ai-orchestrator） */
export async function clearChatSession(sessionId: string): Promise<boolean> {
  const resp = await fetch(`/api/chat/session/${encodeURIComponent(sessionId)}`, {
    method: 'DELETE',
  });
  if (!resp.ok) return false;
  const data = await resp.json().catch(() => null);
  return data?.code === 200;
}