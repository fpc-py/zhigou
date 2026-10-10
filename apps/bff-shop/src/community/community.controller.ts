/**
 * Community Controller — 内容社区（BFF 透传）
 */
import { Controller, Get, Post, Query, Param, Body, Req, UseGuards } from '@nestjs/common';
import { JwtAuthGuard } from '../common/guards/jwt-auth.guard.js';
import { CommunityService } from './community.service.js';
import { ApiResponse } from '../common/dto/api-response.js';
import type { Request } from 'express';

@Controller()
@UseGuards(JwtAuthGuard)
export class CommunityController {
  constructor(private readonly communityService: CommunityService) {}

  @Post('community/note')
  async publish(@Body() body: any, @Req() req: Request) {
    const userId = (req as any).userId as string;
    const data = await this.communityService.publish(userId, body);
    return ApiResponse.ok(data);
  }

  @Get('community/note/page')
  async page(@Query('pageNum') pageNum: string, @Query('pageSize') pageSize: string, @Req() req: Request) {
    const userId = (req as any).userId as string;
    const data = await this.communityService.pageNotes(userId, Number(pageNum || 1), Number(pageSize || 10));
    return ApiResponse.ok(data);
  }

  @Get('community/note/:id')
  async detail(@Param('id') id: string, @Req() req: Request) {
    const userId = (req as any).userId as string;
    const data = await this.communityService.detail(userId, id);
    return ApiResponse.ok(data);
  }

  @Post('community/note/:id/like')
  async like(@Param('id') id: string, @Req() req: Request) {
    const userId = (req as any).userId as string;
    const data = await this.communityService.toggleLike(userId, id);
    return ApiResponse.ok(data);
  }

  @Post('community/note/:id/favorite')
  async favorite(@Param('id') id: string, @Req() req: Request) {
    const userId = (req as any).userId as string;
    const data = await this.communityService.toggleFavorite(userId, id);
    return ApiResponse.ok(data);
  }

  @Post('community/comment')
  async comment(@Body() body: any, @Req() req: Request) {
    const userId = (req as any).userId as string;
    const data = await this.communityService.comment(userId, body);
    return ApiResponse.ok(data);
  }

  @Post('community/ai-writer')
  async aiWriter(@Body() body: any, @Req() req: Request) {
    const userId = (req as any).userId as string;
    const data = await this.communityService.aiWriter(userId, body);
    return ApiResponse.ok(data);
  }

  @Get('community/mine')
  async mine(@Req() req: Request) {
    const userId = (req as any).userId as string;
    const data = await this.communityService.mine(userId);
    return ApiResponse.ok(data);
  }
}
