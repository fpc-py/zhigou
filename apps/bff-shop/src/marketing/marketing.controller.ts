/**
 * Marketing Controller — 优惠券 / 优惠试算 / 拼团（BFF 透传）
 */
import { Controller, Get, Post, Query, Param, Body, Req, UseGuards } from '@nestjs/common';
import { JwtAuthGuard } from '../common/guards/jwt-auth.guard.js';
import { MarketingService } from './marketing.service.js';
import { ApiResponse } from '../common/dto/api-response.js';
import type { Request } from 'express';
import type { DiscountCalculateBody } from './marketing.types.js';

@Controller()
@UseGuards(JwtAuthGuard)
export class MarketingController {
  constructor(private readonly marketingService: MarketingService) {}

  @Get('coupon/mine')
  async myCoupons(@Query('status') status: string | undefined, @Req() req: Request) {
    const userId = (req as any).userId as string;
    const data = await this.marketingService.myCoupons(userId, status);
    return ApiResponse.ok(data);
  }

  @Post('discount/calculate')
  async calculate(@Body() body: DiscountCalculateBody, @Req() req: Request) {
    const userId = (req as any).userId as string;
    const data = await this.marketingService.calculate(userId, body);
    return ApiResponse.ok(data);
  }

  // ==================== 拼团 ====================

  @Get('group-buy/activities')
  async groupBuyActivities(@Req() req: Request) {
    const userId = (req as any).userId as string;
    const data = await this.marketingService.groupBuyActivities(userId);
    return ApiResponse.ok(data);
  }

  @Post('group-buy/open')
  async groupBuyOpen(@Body('activityId') activityId: number, @Req() req: Request) {
    const userId = (req as any).userId as string;
    const data = await this.marketingService.groupBuyOpen(userId, Number(activityId));
    return ApiResponse.ok(data);
  }

  @Post('group-buy/join')
  async groupBuyJoin(@Body('groupId') groupId: number, @Req() req: Request) {
    const userId = (req as any).userId as string;
    const data = await this.marketingService.groupBuyJoin(userId, Number(groupId));
    return ApiResponse.ok(data);
  }

  @Get('group-buy/mine')
  async groupBuyMine(@Req() req: Request) {
    const userId = (req as any).userId as string;
    const data = await this.marketingService.groupBuyMine(userId);
    return ApiResponse.ok(data);
  }

  @Get('group-buy/group/:id')
  async groupBuyDetail(@Param('id') id: string, @Req() req: Request) {
    const userId = (req as any).userId as string;
    const data = await this.marketingService.groupBuyDetail(userId, id);
    return ApiResponse.ok(data);
  }
}
