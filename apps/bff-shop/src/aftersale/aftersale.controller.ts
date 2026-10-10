/**
 * Aftersale Controller — 售后（BFF 透传，JWT 保护）
 */
import { Controller, Get, Post, Param, Body, Req, Query, UseGuards } from '@nestjs/common';
import { JwtAuthGuard } from '../common/guards/jwt-auth.guard.js';
import { AftersaleService } from './aftersale.service.js';
import { ApiResponse } from '../common/dto/api-response.js';
import type { Request } from 'express';
import type { ApplyBody } from './aftersale.types.js';

@Controller('aftersale')
@UseGuards(JwtAuthGuard)
export class AftersaleController {
  constructor(private readonly aftersaleService: AftersaleService) {}

  @Post('apply')
  async apply(@Body() body: ApplyBody, @Req() req: Request) {
    const userId = (req as any).userId as string;
    const data = await this.aftersaleService.apply(userId, body);
    return ApiResponse.ok(data);
  }

  @Get('warranty/alerts')
  async warrantyAlerts(@Req() req: Request, @Query('days') days?: string) {
    const userId = (req as any).userId as string;
    const data = await this.aftersaleService.warrantyAlerts(userId, days ? Number(days) : 30);
    return ApiResponse.ok(data);
  }

  @Post('repair/appointment')
  async createRepairAppointment(@Body() body: Record<string, any>, @Req() req: Request) {
    const userId = (req as any).userId as string;
    const data = await this.aftersaleService.createRepairAppointment(userId, body);
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
