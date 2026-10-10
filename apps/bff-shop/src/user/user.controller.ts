/**
 * User Controller — 用户资料 / 收货地址（BFF 透传）
 */
import { Controller, Get, Post, Put, Delete, Param, Body, Req, UseGuards } from '@nestjs/common';
import { JwtAuthGuard } from '../common/guards/jwt-auth.guard.js';
import { UserService } from './user.service.js';
import { ApiResponse } from '../common/dto/api-response.js';
import type { Request } from 'express';
import type { UserProfileBody, AddressBody, ProductTrackBody } from './user.types.js';

@Controller()
@UseGuards(JwtAuthGuard)
export class UserController {
  constructor(private readonly userService: UserService) {}

  @Get('user/profile')
  async getProfile(@Req() req: Request) {
    const userId = (req as any).userId as string;
    const data = await this.userService.getProfile(userId);
    return ApiResponse.ok(data);
  }

  @Put('user/profile')
  async updateProfile(@Body() body: UserProfileBody, @Req() req: Request) {
    const userId = (req as any).userId as string;
    const data = await this.userService.updateProfile(userId, body);
    return ApiResponse.ok(data);
  }

  // ── 地址 ──

  @Get('address/list')
  async listAddresses(@Req() req: Request) {
    const userId = (req as any).userId as string;
    const data = await this.userService.listAddresses(userId);
    return ApiResponse.ok(data);
  }

  @Post('address')
  async addAddress(@Body() body: AddressBody, @Req() req: Request) {
    const userId = (req as any).userId as string;
    const data = await this.userService.addAddress(userId, body);
    return ApiResponse.ok(data);
  }

  @Put('address/:addressId')
  async updateAddress(@Param('addressId') addressId: string, @Body() body: AddressBody, @Req() req: Request) {
    const userId = (req as any).userId as string;
    const data = await this.userService.updateAddress(userId, addressId, body);
    return ApiResponse.ok(data);
  }

  @Delete('address/:addressId')
  async deleteAddress(@Param('addressId') addressId: string, @Req() req: Request) {
    const userId = (req as any).userId as string;
    await this.userService.deleteAddress(userId, addressId);
    return ApiResponse.ok(null);
  }

  @Put('address/:addressId/default')
  async setDefaultAddress(@Param('addressId') addressId: string, @Req() req: Request) {
    const userId = (req as any).userId as string;
    await this.userService.setDefaultAddress(userId, addressId);
    return ApiResponse.ok(null);
  }

  // ── 收藏 ──

  @Post('user/favorite')
  async addFavorite(@Body() body: ProductTrackBody, @Req() req: Request) {
    const userId = (req as any).userId as string;
    const data = await this.userService.addFavorite(userId, body);
    return ApiResponse.ok(data);
  }

  @Delete('user/favorite/:spuId')
  async removeFavorite(@Param('spuId') spuId: string, @Req() req: Request) {
    const userId = (req as any).userId as string;
    await this.userService.removeFavorite(userId, spuId);
    return ApiResponse.ok(null);
  }

  @Get('user/favorite')
  async listFavorites(@Req() req: Request) {
    const userId = (req as any).userId as string;
    const page = Number((req as any).query?.page ?? 1);
    const size = Number((req as any).query?.size ?? 10);
    const data = await this.userService.listFavorites(userId, page, size);
    return ApiResponse.ok(data);
  }

  @Get('user/favorite/ids')
  async favoriteIds(@Req() req: Request) {
    const userId = (req as any).userId as string;
    const data = await this.userService.favoriteIds(userId);
    return ApiResponse.ok(data);
  }

  @Get('user/favorite/check')
  async checkFavorite(@Req() req: Request) {
    const userId = (req as any).userId as string;
    const spuId = (req as any).query?.spuId as string;
    const data = await this.userService.checkFavorite(userId, spuId);
    return ApiResponse.ok(data);
  }

  // ── 浏览历史 ──

  @Post('user/browse')
  async recordBrowse(@Body() body: ProductTrackBody, @Req() req: Request) {
    const userId = (req as any).userId as string;
    const data = await this.userService.recordBrowse(userId, body);
    return ApiResponse.ok(data);
  }

  @Get('user/browse/recent')
  async recentBrowse(@Req() req: Request) {
    const userId = (req as any).userId as string;
    const limit = Number((req as any).query?.limit ?? 20);
    const data = await this.userService.recentBrowse(userId, limit);
    return ApiResponse.ok(data);
  }

  // ── 用户画像 ──

  @Get('user/insight')
  async insight(@Req() req: Request) {
    const userId = (req as any).userId as string;
    const data = await this.userService.insight(userId);
    return ApiResponse.ok(data);
  }
}
