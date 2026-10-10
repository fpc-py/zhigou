/**
 * Merchant Controller — 商家经营概览（BFF 透传；演示：单商家市场 = 平台聚合口径）
 */
import { Body, Controller, Get, Post, Query, UseGuards } from '@nestjs/common';
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

  /** 销量预测（7/30 天，演示口径） */
  @Get('merchant/forecast')
  async forecast(@Query('days') days?: string) {
    const data = await this.merchant.forecast(days ? Number(days) : 7);
    return ApiResponse.ok(data);
  }

  /** 智能选品（热度/库存/趋势，演示口径） */
  @Get('merchant/selection')
  async selection() {
    const data = await this.merchant.selection();
    return ApiResponse.ok(data);
  }

  /** 供应链补货中心：最近补货记录 */
  @Get('merchant/supply/records')
  async replenishRecords(@Query('limit') limit?: string) {
    const data = await this.merchant.replenishRecords(limit ? Number(limit) : 10);
    return ApiResponse.ok(data);
  }

  /** 供应链补货中心：最近异常订单处理记录 */
  @Get('merchant/fulfillment/actions')
  async fulfillmentActions(@Query('limit') limit?: string) {
    const data = await this.merchant.fulfillmentActions(limit ? Number(limit) : 10);
    return ApiResponse.ok(data);
  }

  /** 供应链补货中心：异常订单自动处理（SPLIT/DELAY/OFF_SHELF/REPLENISH，演示口径） */
  @Post('merchant/fulfillment/action')
  async fulfillmentAction(@Body() body: { action: string; orderIds?: string[]; reason?: string }) {
    const data = await this.merchant.fulfillmentAction(body.action, body.orderIds ?? [], body.reason);
    return ApiResponse.ok(data);
  }

  /** 供应链补货中心：自动补货（低库存补到目标库存，演示口径） */
  @Post('merchant/supply/auto-replenish')
  async autoReplenish(@Body() body: { threshold?: number; targetQty?: number }) {
    const data = await this.merchant.autoReplenish(body?.threshold ?? 10, body?.targetQty ?? 50);
    return ApiResponse.ok(data);
  }

  /** 供应链补货中心：手动补货指定 SKU（演示口径） */
  @Post('merchant/supply/replenish')
  async manualReplenish(@Body() body: { skuId: number; addQty: number; remark?: string }) {
    const data = await this.merchant.manualReplenish(body.skuId, body.addQty, body.remark);
    return ApiResponse.ok(data);
  }
}
