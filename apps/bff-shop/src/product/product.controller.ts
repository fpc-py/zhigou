/**
 * Product Controller — 商品详情聚合
 */
import { Controller, Get, Param, Req, UseGuards } from '@nestjs/common';
import { JwtAuthGuard } from '../common/guards/jwt-auth.guard.js';
import { ProductService } from './product.service.js';
import { ApiResponse } from '../common/dto/api-response.js';
import type { Request } from 'express';

@Controller('product')
@UseGuards(JwtAuthGuard)
export class ProductController {
  constructor(private readonly productService: ProductService) {}

  @Get(':spuId/detail')
  async getDetail(@Param('spuId') spuId: string, @Req() req: Request) {
    const userId = (req as any).userId;
    const data = await this.productService.getDetail(spuId, userId);
    return ApiResponse.ok(data);
  }
}