/**
 * Order Controller — 订单（BFF 透传）
 */
import { Controller, Get, Post, Param, Body, Req, UseGuards } from '@nestjs/common';
import type { RawBodyRequest } from '@nestjs/common';
import { JwtAuthGuard } from '../common/guards/jwt-auth.guard.js';
import { OrderService } from './order.service.js';
import { ApiResponse } from '../common/dto/api-response.js';
import type { Request } from 'express';

@Controller('order')
@UseGuards(JwtAuthGuard)
export class OrderController {
  constructor(private readonly orderService: OrderService) {}

  @Get('mine')
  async mine(@Req() req: Request) {
    const userId = (req as any).userId as string;
    const data = await this.orderService.mine(userId);
    return ApiResponse.ok(data);
  }

  /**
   * 坑 #63 修复：create 用原始 body 字符串透传（不经过 JSON.parse → stringify）。
   * 19 位 Snowflake skuId（如 9000000000000000020）超 JS 安全整数（2^53），
   * NestJS body 解析后转 number 即失真（→9000000000000000000），Java 侧查不到
   * 该 SKU → 订单金额落占位价。rawBody 原样转发保留精度。
   */
  @Post('create')
  async create(@Req() req: RawBodyRequest<Request>) {
    const userId = (req as any).userId as string;
    const raw = req.rawBody?.toString() ?? '{}';
    const data = await this.orderService.create(userId, raw);
    return ApiResponse.ok(data);
  }

  @Post(':orderId/cancel')
  async cancel(@Param('orderId') orderId: string, @Req() req: Request) {
    const userId = (req as any).userId as string;
    await this.orderService.cancel(userId, orderId);
    return ApiResponse.ok(null);
  }

  @Get(':orderId')
  async detail(@Param('orderId') orderId: string, @Req() req: Request) {
    const userId = (req as any).userId as string;
    const data = await this.orderService.detail(userId, orderId);
    return ApiResponse.ok(data);
  }

  /** 部分发货（A4）：坑 #63 精度修复——rawBody 字符串透传（skuId 19 位防 JS 精度丢失） */
  @Post(':orderId/ship-partial')
  async shipPartial(@Param('orderId') orderId: string, @Req() req: RawBodyRequest<Request>) {
    const userId = (req as any).userId as string;
    const raw = req.rawBody?.toString() ?? '{}';
    const data = await this.orderService.shipPartial(userId, orderId, raw);
    return ApiResponse.ok(data);
  }

  @Get(':orderId/shipments')
  async shipments(@Param('orderId') orderId: string) {
    const data = await this.orderService.shipments(orderId);
    return ApiResponse.ok(data);
  }
}
