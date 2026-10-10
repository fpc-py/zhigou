/**
 * Closet Controller — 智能衣橱 + 家居管理（BFF 透传，全链路登录态由 JwtAuthGuard 保障）
 */
import { Controller, Get, Post, Delete, Query, Param, Body, Req, UseGuards } from '@nestjs/common';
import { JwtAuthGuard } from '../common/guards/jwt-auth.guard.js';
import { ClosetService } from './closet.service.js';
import { ApiResponse } from '../common/dto/api-response.js';
import type { Request } from 'express';

@Controller()
@UseGuards(JwtAuthGuard)
export class ClosetController {
  constructor(private readonly closet: ClosetService) {}

  private uid(req: Request): string {
    return (req as any).userId as string;
  }

  /** 我的衣物 */
  @Get('closet/items')
  async items(@Query('category') category: string | undefined, @Query('season') season: string | undefined, @Req() req: Request) {
    const data = await this.closet.myItems(this.uid(req), category, season);
    return ApiResponse.ok(data);
  }

  /** 添加衣物 */
  @Post('closet/item')
  async add(@Body() body: any, @Req() req: Request) {
    const data = await this.closet.addItem(this.uid(req), body);
    return ApiResponse.ok(data);
  }

  /** 穿着打卡 */
  @Post('closet/item/:id/wear')
  async wear(@Param('id') id: string, @Req() req: Request) {
    const data = await this.closet.wear(this.uid(req), id);
    return ApiResponse.ok(data);
  }

  /** 删除衣物 */
  @Delete('closet/item/:id')
  async remove(@Param('id') id: string, @Req() req: Request) {
    const data = await this.closet.removeItem(this.uid(req), id);
    return ApiResponse.ok(data);
  }

  /** 穿搭推荐 */
  @Get('closet/outfit/recommend')
  async recommend(@Query('occasion') occasion: string | undefined, @Req() req: Request) {
    const data = await this.closet.recommendOutfit(this.uid(req), occasion);
    return ApiResponse.ok(data);
  }

  /** 家居盘点 */
  @Get('closet/home/list')
  async assets(@Query('category') category: string | undefined, @Req() req: Request) {
    const data = await this.closet.myAssets(this.uid(req), category);
    return ApiResponse.ok(data);
  }

  /** 添加家居物品 */
  @Post('closet/home/item')
  async addAsset(@Body() body: any, @Req() req: Request) {
    const data = await this.closet.addAsset(this.uid(req), body);
    return ApiResponse.ok(data);
  }

  /** 补货清单 */
  @Get('closet/home/replenish')
  async replenish(@Req() req: Request) {
    const data = await this.closet.replenishList(this.uid(req));
    return ApiResponse.ok(data);
  }
}
