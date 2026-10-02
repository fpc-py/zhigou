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
    '/api/v1/chat/sse',
    { query, userId: '', sessionId }, // userId 由 BFF 从 JWT 提取
    onMessage,
    signal,
  );
}