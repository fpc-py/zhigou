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

  /** 领取优惠券（templateId 后端为 long，前端字符串透传避免精度丢失） */
  @Post('coupon/issue')
  async couponIssue(@Body('templateId') templateId: string, @Req() req: Request) {
    const userId = (req as any).userId as string;
    const data = await this.marketingService.issueCoupon(userId, templateId);
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

  /** 开团：坑 #63 精度修复——activityId 19 位统一字符串透传（防 JS 精度丢失） */
  @Post('group-buy/open')
  async groupBuyOpen(@Body('activityId') activityId: string, @Req() req: Request) {
    const userId = (req as any).userId as string;
    const data = await this.marketingService.groupBuyOpen(userId, activityId);
    return ApiResponse.ok(data);
  }

  /** 参团：坑 #63 精度修复——groupId 19 位统一字符串透传 */
  @Post('group-buy/join')
  async groupBuyJoin(@Body('groupId') groupId: string, @Req() req: Request) {
    const userId = (req as any).userId as string;
    const data = await this.marketingService.groupBuyJoin(userId, groupId);
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

  // ==================== 秒杀 ====================

  @Get('seckill/activities')
  async seckillActivities(@Req() req: Request) {
    const userId = (req as any).userId as string;
    const data = await this.marketingService.seckillActivities(userId);
    return ApiResponse.ok(data);
  }

  @Get('seckill/activity/:id')
  async seckillDetail(@Param('id') id: string, @Req() req: Request) {
    const userId = (req as any).userId as string;
    const data = await this.marketingService.seckillDetail(userId, id);
    return ApiResponse.ok(data);
  }

  /** 秒杀预扣（防超卖）：坑 #63 精度修复——activityId 19 位统一字符串透传 */
  @Post('seckill/buy')
  async seckillBuy(@Body() body: { activityId: string; count?: number }, @Req() req: Request) {
    const userId = (req as any).userId as string;
    const data = await this.marketingService.seckillBuy(userId, body);
    return ApiResponse.ok(data);
  }

  /** 秒杀预扣释放：坑 #63 精度修复——activityId 19 位统一字符串透传 */
  @Post('seckill/release')
  async seckillRelease(@Body() body: { activityId: string; count?: number }, @Req() req: Request) {
    const userId = (req as any).userId as string;
    const data = await this.marketingService.seckillRelease(userId, body);
    return ApiResponse.ok(data);
  }
}
