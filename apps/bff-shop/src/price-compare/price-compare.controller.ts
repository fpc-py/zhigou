/**
 * Price Compare Controller — POST /price/compare
 */
import { Body, Controller, Post, Req, UseGuards } from '@nestjs/common';
import { JwtAuthGuard } from '../common/guards/jwt-auth.guard.js';
import { PriceCompareService } from './price-compare.service.js';
import { ApiResponse } from '../common/dto/api-response.js';
import type { Request } from 'express';

@Controller('price')
@UseGuards(JwtAuthGuard)
export class PriceCompareController {
  constructor(private readonly priceCompareService: PriceCompareService) {}

  /** 跨平台比价：入参 SKU ID 列表 */
  @Post('/compare')
  async compare(@Req() req: Request, @Body() skuIds: number[]) {
    const userId = (req as any).userId;
    const data = await this.priceCompareService.compare(userId, skuIds);
    return ApiResponse.ok(data);
  }
}
