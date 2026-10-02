/**
 * Home Controller — 首页聚合
 */
import { Controller, Get, Req, UseGuards } from '@nestjs/common';
import { JwtAuthGuard } from '../common/guards/jwt-auth.guard.js';
import { HomeService } from './home.service.js';
import { ApiResponse } from '../common/dto/api-response.js';
import type { Request } from 'express';

@Controller('home')
@UseGuards(JwtAuthGuard)
export class HomeController {
  constructor(private readonly homeService: HomeService) {}

  @Get('feed')
  async getFeed(@Req() req: Request) {
    const userId = (req as any).userId;
    const data = await this.homeService.getFeed(userId);
    return ApiResponse.ok(data);
  }
}