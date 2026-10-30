/**
 * Product Controller — 商品分页 + 详情聚合
 */
import { Controller, Get, Param, Post, Put, Delete, Body, Query, Req, UseGuards } from '@nestjs/common';
import { JwtAuthGuard } from '../common/guards/jwt-auth.guard.js';
import { RolesGuard, Roles } from '../common/guards/roles.guard.js';
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

  /** 新增商品（仅商家） */
  @Post('spu')
  @UseGuards(RolesGuard)
  @Roles('MERCHANT')
  async createSpu(@Body() body: any, @Req() req: Request) {
    const userId = (req as any).userId;
    const spuId = await this.productService.createSpu(body, userId);
    return ApiResponse.ok({ spuId });
  }

  /** 更新商品（仅商家） */
  @Put('spu/:spuId')
  @UseGuards(RolesGuard)
  @Roles('MERCHANT')
  async updateSpu(@Param('spuId') spuId: string, @Body() body: any, @Req() req: Request) {
    const userId = (req as any).userId;
    const ok = await this.productService.updateSpu(spuId, body, userId);
    return ApiResponse.ok(ok);
  }

  /** 下架商品（仅商家） */
  @Delete('spu/:spuId')
  @UseGuards(RolesGuard)
  @Roles('MERCHANT')
  async offShelf(@Param('spuId') spuId: string, @Req() req: Request) {
    const userId = (req as any).userId;
    const ok = await this.productService.offShelf(spuId, userId);
    return ApiResponse.ok(ok);
  }

  @Get(':spuId/detail')
  async getDetail(@Param('spuId') spuId: string, @Req() req: Request) {
    const userId = (req as any).userId;
    const data = await this.productService.getDetail(spuId, userId);
    return ApiResponse.ok(data);
  }
}