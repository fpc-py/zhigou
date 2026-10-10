/**
 * Life Controller — 本地生活（BFF 透传，全链路登录态由 JwtAuthGuard 保障）
 */
import { Controller, Get, Post, Query, Param, Body, Req, UseGuards } from '@nestjs/common';
import { JwtAuthGuard } from '../common/guards/jwt-auth.guard.js';
import { LifeService } from './life.service.js';
import { ApiResponse } from '../common/dto/api-response.js';
import type { Request } from 'express';

@Controller()
@UseGuards(JwtAuthGuard)
export class LifeController {
  constructor(private readonly life: LifeService) {}

  private uid(req: Request): string {
    return (req as any).userId as string;
  }

  /** 门店/商圈分页 */
  @Get('life/poi/page')
  async pageStores(
    @Query('category') category: string | undefined,
    @Query('pageNum') pageNum: string,
    @Query('pageSize') pageSize: string,
    @Req() req: Request,
  ) {
    const data = await this.life.pageStores(this.uid(req), category, Number(pageNum || 1), Number(pageSize || 20));
    return ApiResponse.ok(data);
  }

  /** 门店详情（含服务 SKU） */
  @Get('life/poi/:id')
  async storeDetail(@Param('id') id: string, @Req() req: Request) {
    const data = await this.life.storeDetail(this.uid(req), id);
    return ApiResponse.ok(data);
  }

  /** 服务 SKU 分页 */
  @Get('life/sku/page')
  async pageSkus(
    @Query('category') category: string | undefined,
    @Query('pageNum') pageNum: string,
    @Query('pageSize') pageSize: string,
    @Req() req: Request,
  ) {
    const data = await this.life.pageSkus(this.uid(req), category, Number(pageNum || 1), Number(pageSize || 20));
    return ApiResponse.ok(data);
  }

  /** 创建到店/服务预约 */
  @Post('life/appointment')
  async create(@Body() body: any, @Req() req: Request) {
    const data = await this.life.createAppointment(this.uid(req), body);
    return ApiResponse.ok(data);
  }

  /** 我的预约 */
  @Get('life/appointment/mine')
  async mine(@Req() req: Request) {
    const data = await this.life.myAppointments(this.uid(req));
    return ApiResponse.ok(data);
  }

  /** 取消预约 */
  @Post('life/appointment/:id/cancel')
  async cancel(@Param('id') id: string, @Req() req: Request) {
    const data = await this.life.cancelAppointment(this.uid(req), id);
    return ApiResponse.ok(data);
  }
}
