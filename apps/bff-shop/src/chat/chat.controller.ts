/**
 * Chat Controller — SSE 透传 + 会话管理
 *
 * POST /chat/sse      body: { query, sessionId }
 * DELETE /chat/session/:sessionId   清空会话历史
 *
 * 注意：直接用 @Res() 接管响应，不经过 NestJS 的拦截器/过滤器链
 */
import { Controller, Post, Delete, Body, Param, Req, Res, UseGuards, HttpException, HttpStatus } from '@nestjs/common';
import { JwtAuthGuard } from '../common/guards/jwt-auth.guard.js';
import { ChatService } from './chat.service.js';
import type { Request, Response } from 'express';

@Controller('chat')
@UseGuards(JwtAuthGuard)
export class ChatController {
  constructor(private readonly chatService: ChatService) {}

  @Post('sse')
  async chat(
    @Body('query') query: string,
    @Body('sessionId') sessionId: string,
    @Req() req: Request,
    @Res() res: Response,
  ) {
    const userId = (req as any).userId ?? 'anonymous';
    await this.chatService.streamChat(query ?? '', userId, sessionId ?? '', res);
  }

  /** 清空指定会话历史（透传 ai-orchestrator） */
  @Delete('session/:sessionId')
  async clearSession(@Param('sessionId') sessionId: string) {
    const ok = await this.chatService.clearSession(sessionId);
    if (!ok) {
      throw new HttpException('会话不存在或已清空', HttpStatus.NOT_FOUND);
    }
    return { code: 200, message: '会话已清空' };
  }
}