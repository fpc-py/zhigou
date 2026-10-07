/**
 * Product Controller — 商品分页 + 详情聚合
 */
import { Controller, Get, Param, Query, Req, UseGuards } from '@nestjs/common';
import { JwtAuthGuard } from '../common/guards/jwt-auth.guard.js';
import { ProductService } from './product.service.js';
import { ApiResponse } from '../common/dto/api-response.js';
import type { Request } from 'express';

@Controller('product')
@UseGuards(JwtAuthGuard)
export class ProductController {
  constructor(private readonly productService: ProductService) {}

  @Get('page')
  async page(
    @Query('keyword') keyword: string | undefined,
    @Query('pageNum') pageNum: string | undefined,
    @Query('pageSize') pageSize: string | undefined,
    @Query('categoryId') categoryId: string | undefined,
    @Query('brandId') brandId: string | undefined,
    @Req() req: Request,
  ) {
    const userId = (req as any).userId;
    const data = await this.productService.page(userId, {
      keyword,
      pageNum: pageNum ? Number(pageNum) : undefined,
      pageSize: pageSize ? Number(pageSize) : undefined,
      categoryId: categoryId ? Number(categoryId) : undefined,
      brandId: brandId ? Number(brandId) : undefined,
    });
    return ApiResponse.ok(data);
  }

  @Get(':spuId/detail')
  async getDetail(@Param('spuId') spuId: string, @Req() req: Request) {
    const userId = (req as any).userId;
    const data = await this.productService.getDetail(spuId, userId);
    return ApiResponse.ok(data);
  }
}