/**
 * Chat Service — SSE 透传
 * 通过 axios stream 零缓冲转发 ai-orchestrator 的 SSE
 */
import { Injectable, Logger } from '@nestjs/common';
import axios from 'axios';
import { SERVICES, SERVICE_PATHS } from '../config/service.config.js';

@Injectable()
export class ChatService {
  private readonly logger = new Logger(ChatService.name);

  /**
   * 透传 SSE 流到响应对象
   * @param query 用户输入
   * @param userId 用户 ID
   * @param sessionId 会话 ID
   * @param imageUrl 图片 URL（图片搜款，可空）
   * @param res Express Response 对象
   */
  async streamChat(query: string, userId: string, sessionId: string, imageUrl: string, res: any): Promise<void> {
    try {
      const aiResp = await axios.post(
        `${SERVICES.aiOrchestrator.url}${SERVICE_PATHS.chatSse}`,
        { query, userId, sessionId, imageUrl },
        {
          responseType: 'stream',
          timeout: SERVICES.aiOrchestrator.timeout,
          headers: {
            'Content-Type': 'application/json',
            'x-user-id': userId,
          },
        },
      );

      // 设置 SSE 响应头
      res.setHeader('Content-Type', 'text/event-stream');
      res.setHeader('Cache-Control', 'no-cache');
      res.setHeader('Connection', 'keep-alive');
      res.setHeader('X-Accel-Buffering', 'no');

      // 零缓冲透传
      aiResp.data.pipe(res);

      // 错误和完成处理
      aiResp.data.on('error', (err: Error) => {
        this.logger.error(`SSE 流错误: ${err.message}`);
        res.end();
      });

      res.on('close', () => {
        aiResp.data.destroy();
      });
    } catch (err: any) {
      this.logger.error(`ai-orchestrator SSE 连接失败: ${err.message}`);
      res.setHeader('Content-Type', 'text/event-stream');
      res.write(`event: error\ndata: {"message":"AI 助手暂时不可用"}\n\n`);
      res.write(`event: done\ndata: null\n\n`);
      res.end();
    }
  }

  /** 清空指定会话历史（透传 ai-orchestrator DELETE /api/v1/chat/session/:id） */
  async clearSession(sessionId: string): Promise<boolean> {
    try {
      const resp = await axios.delete(
        `${SERVICES.aiOrchestrator.url}/api/v1/chat/session/${encodeURIComponent(sessionId)}`,
        { timeout: SERVICES.aiOrchestrator.timeout },
      );
      return resp.data?.code === 200;
    } catch (err: any) {
      this.logger.warn(`清空会话失败 session=%s: ${err.message}`, sessionId);
      return false;
    }
  }
}