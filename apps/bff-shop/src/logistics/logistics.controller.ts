/**
 * Logistics Controller — 运费计算（BFF 透传）
 */
import { Controller, Post, Body, UseGuards } from '@nestjs/common';
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
}
