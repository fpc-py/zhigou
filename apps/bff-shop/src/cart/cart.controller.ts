/**
 * Cart Controller — 购物车（BFF 聚合）
 */
import { Controller, Get, Post, Delete, Param, Req, UseGuards } from '@nestjs/common';
import type { RawBodyRequest } from '@nestjs/common';
import { JwtAuthGuard } from '../common/guards/jwt-auth.guard.js';
import { CartService } from './cart.service.js';
import { ApiResponse } from '../common/dto/api-response.js';
import type { Request } from 'express';

@Controller('cart')
@UseGuards(JwtAuthGuard)
export class CartController {
  constructor(private readonly cartService: CartService) {}

  @Get('mine')
  async mine(@Req() req: Request) {
    const userId = (req as any).userId as string;
    const data = await this.cartService.mine(userId);
    return ApiResponse.ok(data);
  }

  /** 加购：坑 #63 精度修复——rawBody 字符串透传（skuId 19 位防 JS 精度丢失） */
  @Post('add')
  async add(@Req() req: RawBodyRequest<Request>) {
    const userId = (req as any).userId as string;
    const raw = req.rawBody?.toString() ?? '{}';
    await this.cartService.add(userId, raw);
    return ApiResponse.ok(null);
  }

  /** 更新购物车项：坑 #63 精度修复——rawBody 字符串透传 */
  @Post('update')
  async update(@Req() req: RawBodyRequest<Request>) {
    const userId = (req as any).userId as string;
    const raw = req.rawBody?.toString() ?? '{}';
    await this.cartService.update(userId, raw);
    return ApiResponse.ok(null);
  }

  @Post('clear')
  async clear(@Req() req: Request) {
    const userId = (req as any).userId as string;
    await this.cartService.clear(userId);
    return ApiResponse.ok(null);
  }

  @Delete(':skuId')
  async remove(@Param('skuId') skuId: string, @Req() req: Request) {
    const userId = (req as any).userId as string;
    await this.cartService.remove(userId, skuId);
    return ApiResponse.ok(null);
  }
}
