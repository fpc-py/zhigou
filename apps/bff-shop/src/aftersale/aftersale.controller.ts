/**
 * Aftersale Controller — 售后（BFF 透传，JWT 保护）
 */
import { Controller, Get, Post, Param, Body, Req, Query, UseGuards } from '@nestjs/common';
import type { RawBodyRequest } from '@nestjs/common';
import { JwtAuthGuard } from '../common/guards/jwt-auth.guard.js';
import { AftersaleService } from './aftersale.service.js';
import { ApiResponse } from '../common/dto/api-response.js';
import type { Request } from 'express';

@Controller('aftersale')
@UseGuards(JwtAuthGuard)
export class AftersaleController {
  constructor(private readonly aftersaleService: AftersaleService) {}

  /** 申请售后：坑 #63 精度修复——rawBody 字符串透传（orderId/skuId 19 位防 JS 精度丢失） */
  @Post('apply')
  async apply(@Req() req: RawBodyRequest<Request>) {
    const userId = (req as any).userId as string;
    const raw = req.rawBody?.toString() ?? '{}';
    const data = await this.aftersaleService.apply(userId, raw);
    return ApiResponse.ok(data);
  }

  @Get('warranty/alerts')
  async warrantyAlerts(@Req() req: Request, @Query('days') days?: string) {
    const userId = (req as any).userId as string;
    const data = await this.aftersaleService.warrantyAlerts(userId, days ? Number(days) : 30);
    return ApiResponse.ok(data);
  }

  /** 创建维修预约：坑 #63 精度修复——rawBody 字符串透传（含 19 位 skuId 防失真） */
  @Post('repair/appointment')
  async createRepairAppointment(@Req() req: RawBodyRequest<Request>) {
    const userId = (req as any).userId as string;
    const raw = req.rawBody?.toString() ?? '{}';
    const data = await this.aftersaleService.createRepairAppointment(userId, raw);
    return ApiResponse.ok(data);
  }

  @Get('repair/appointments')
  async repairAppointments(@Req() req: Request) {
    const userId = (req as any).userId as string;
    const data = await this.aftersaleService.repairAppointments(userId);
    return ApiResponse.ok(data);
  }

  @Get('mine')
  async mine(@Req() req: Request) {
    const userId = (req as any).userId as string;
    const data = await this.aftersaleService.mine(userId);
    return ApiResponse.ok(data);
  }

  @Get(':no')
  async detail(@Param('no') no: string, @Req() req: Request) {
    const userId = (req as any).userId as string;
    const data = await this.aftersaleService.detail(userId, no);
    return ApiResponse.ok(data);
  }

  @Post(':no/cancel')
  async cancel(@Param('no') no: string, @Req() req: Request) {
    const userId = (req as any).userId as string;
    await this.aftersaleService.cancel(userId, no);
    return ApiResponse.ok(null);
  }
}
