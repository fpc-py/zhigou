/**
 * Reminders Controller — AI 主动提醒中心（BFF 透传）
 *
 * GET  reminders/mine?unreadOnly=true   我的提醒列表
 * POST reminders/scan                   手动触发一次扫描（当前用户）
 * POST reminders/read/:id               标记已读
 *
 * 契约同 life/closet：全量 JWT 鉴权，出站注入 X-User-Id；
 * 业务失败 HTTP200+code!==200 → HttpException。
 */
import { Controller, Get, Post, Param, Query, Body, Req, UseGuards } from '@nestjs/common';
import { JwtAuthGuard } from '../common/guards/jwt-auth.guard.js';
import { RemindersService } from './reminders.service.js';
import { ApiResponse } from '../common/dto/api-response.js';
import type { Request } from 'express';

@Controller()
@UseGuards(JwtAuthGuard)
export class RemindersController {
  constructor(private readonly reminders: RemindersService) {}

  private uid(req: Request): string {
    return (req as any).userId as string;
  }

  /** 我的提醒列表（AI 主动提醒中心） */
  @Get('reminders/mine')
  async mine(@Query('unreadOnly') unreadOnly: string | undefined, @Req() req: Request) {
    const data = await this.reminders.mine(this.uid(req), unreadOnly === 'true');
    return ApiResponse.ok(data);
  }

  /** 手动触发一次扫描 */
  @Post('reminders/scan')
  async scan(@Req() req: Request) {
    const data = await this.reminders.scan(this.uid(req));
    return ApiResponse.ok(data);
  }

  /** 标记已读 */
  @Post('reminders/read/:id')
  async markRead(@Param('id') id: string, @Req() req: Request) {
    const data = await this.reminders.markRead(this.uid(req), id);
    return ApiResponse.ok(data);
  }
}
