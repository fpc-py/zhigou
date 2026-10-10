/**
 * Merchant Controller — 商家经营概览（BFF 透传；演示：单商家市场 = 平台聚合口径）
 */
import { Controller, Get, UseGuards } from '@nestjs/common';
import { JwtAuthGuard } from '../common/guards/jwt-auth.guard.js';
import { MerchantService } from './merchant.service.js';
import { ApiResponse } from '../common/dto/api-response.js';

@Controller()
@UseGuards(JwtAuthGuard)
export class MerchantController {
  constructor(private readonly merchant: MerchantService) {}

  /** 商家经营概览 */
  @Get('merchant/overview')
  async overview() {
    const data = await this.merchant.overview();
    return ApiResponse.ok(data);
  }

  /** 商家经营预警（低库存 + 差评） */
  @Get('merchant/warnings')
  async warnings() {
    const data = await this.merchant.warnings();
    return ApiResponse.ok(data);
  }

  /** 履约异常（待发货订单 + SKU 明细，缺货/卡单预警） */
  @Get('merchant/fulfillment')
  async fulfillment() {
    const data = await this.merchant.fulfillment();
    return ApiResponse.ok(data);
  }
}
