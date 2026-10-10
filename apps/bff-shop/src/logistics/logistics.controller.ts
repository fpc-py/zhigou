/**
 * Logistics Controller — 运费计算（BFF 透传）
 */
import { Controller, Get, Post, Body, Query, UseGuards } from '@nestjs/common';
import { JwtAuthGuard } from '../common/guards/jwt-auth.guard.js';
import { LogisticsService } from './logistics.service.js';
import { ApiResponse } from '../common/dto/api-response.js';
import type { FreightCalculateBody } from './logistics.types.js';

@Controller('freight')
@UseGuards(JwtAuthGuard)
export class LogisticsController {
  constructor(private readonly logisticsService: LogisticsService) {}

  @Post('calculate')
  async calculate(@Body() body: FreightCalculateBody) {
    const data = await this.logisticsService.calculateFreight(body);
    return ApiResponse.ok(data);
  }

  @Get('delay-alerts')
  async delayAlerts(@Query('stagnantHours') stagnantHours?: string) {
    const data = await this.logisticsService.delayAlerts(stagnantHours ? Number(stagnantHours) : 48);
    return ApiResponse.ok(data);
  }

  @Post('dispatch')
  async dispatch(@Body() body: { shipmentNo: string; action: string; reason?: string }) {
    const data = await this.logisticsService.dispatch(body.shipmentNo, body.action, body.reason);
    return ApiResponse.ok(data);
  }
}
