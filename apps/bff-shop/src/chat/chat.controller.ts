/**
 * Chat Controller — SSE 透传
 *
 * POST /chat/sse  body: { query, sessionId }
 *
 * 注意：直接用 @Res() 接管响应，不经过 NestJS 的拦截器/过滤器链
 */
import { Controller, Post, Body, Req, Res, UseGuards } from '@nestjs/common';
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
}