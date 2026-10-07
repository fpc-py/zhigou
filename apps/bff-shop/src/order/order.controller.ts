/**
 * Order Controller — 订单（BFF 透传）
 */
import { Controller, Get, Post, Param, Body, Req, UseGuards } from '@nestjs/common';
import { JwtAuthGuard } from '../common/guards/jwt-auth.guard.js';
import { OrderService } from './order.service.js';
import { ApiResponse } from '../common/dto/api-response.js';
import type { Request } from 'express';
import type { CreateOrderBody } from './order.types.js';

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

  @Post('create')
  async create(@Body() body: CreateOrderBody, @Req() req: Request) {
    const userId = (req as any).userId as string;
    const data = await this.orderService.create(userId, body);
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
}
