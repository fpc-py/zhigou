/**
 * Cart Controller — 购物车（BFF 聚合）
 */
import { Controller, Get, Post, Delete, Param, Body, Req, UseGuards } from '@nestjs/common';
import { JwtAuthGuard } from '../common/guards/jwt-auth.guard.js';
import { CartService } from './cart.service.js';
import { ApiResponse } from '../common/dto/api-response.js';
import type { Request } from 'express';
import type { CartAddBody, CartUpdateBody } from './cart.types.js';

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

  @Post('add')
  async add(@Body() body: CartAddBody, @Req() req: Request) {
    const userId = (req as any).userId as string;
    await this.cartService.add(userId, body);
    return ApiResponse.ok(null);
  }

  @Post('update')
  async update(@Body() body: CartUpdateBody, @Req() req: Request) {
    const userId = (req as any).userId as string;
    await this.cartService.update(userId, body);
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
