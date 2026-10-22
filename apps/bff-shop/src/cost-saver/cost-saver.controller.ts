/**
 * Cost Saver Controller — AI 省钱助手（C 块 P0）
 * GET /cost-saver/overview + /price/alert 关注管理 + /price/notice 降价提醒
 */
import {
  Body, Controller, Delete, Get, Param, Post, Query, Req, UseGuards,
} from '@nestjs/common';
import { JwtAuthGuard } from '../common/guards/jwt-auth.guard.js';
import { CostSaverService } from './cost-saver.service.js';
import { ApiResponse } from '../common/dto/api-response.js';
import type { Request } from 'express';

@Controller()
@UseGuards(JwtAuthGuard)
export class CostSaverController {
  constructor(private readonly costSaverService: CostSaverService) {}

  /** 省钱总览：满减实省 + 最优券预估 + 比价最优差 + 降价已省（第一屏可感知） */
  @Get('/cost-saver/overview')
  async overview(@Req() req: Request) {
    const userId = (req as any).userId;
    return ApiResponse.ok(await this.costSaverService.overview(userId));
  }

  /** 历史价格趋势 */
  @Get('/price/history')
  async history(@Req() req: Request, @Query('skuId') skuId: string, @Query('days') days?: string) {
    const userId = (req as any).userId;
    return ApiResponse.ok(await this.costSaverService.history(userId, skuId, days ?? '30'));
  }

  /** 关注降价（幂等 upsert；targetPrice 可空=任意降价） */
  @Post('/price/alert')
  async watch(@Req() req: Request, @Body() body: { skuId: string | number; targetPrice?: string | number | null }) {
    const userId = (req as any).userId;
    return ApiResponse.ok(await this.costSaverService.watch(userId, body));
  }

  /** 取消关注 */
  @Delete('/price/alert/:skuId')
  async unwatch(@Req() req: Request, @Param('skuId') skuId: string) {
    const userId = (req as any).userId;
    return ApiResponse.ok(await this.costSaverService.unwatch(userId, skuId));
  }

  /** 我的关注列表 */
  @Get('/price/alert/mine')
  async mine(@Req() req: Request) {
    const userId = (req as any).userId;
    return ApiResponse.ok(await this.costSaverService.mine(userId));
  }

  /** 我的降价提醒（unreadOnly=true 仅未读） */
  @Get('/price/notice/mine')
  async notices(@Req() req: Request, @Query('unreadOnly') unreadOnly?: string) {
    const userId = (req as any).userId;
    return ApiResponse.ok(await this.costSaverService.notices(userId, unreadOnly === 'true'));
  }

  /** 标记提醒已读 */
  @Post('/price/notice/read/:noticeId')
  async markRead(@Req() req: Request, @Param('noticeId') noticeId: string) {
    const userId = (req as any).userId;
    return ApiResponse.ok(await this.costSaverService.markRead(userId, noticeId));
  }
}
