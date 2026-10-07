/**
 * Marketing Controller — 优惠券 / 优惠试算（BFF 透传）
 */
import { Controller, Get, Post, Query, Body, Req, UseGuards } from '@nestjs/common';
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
}
