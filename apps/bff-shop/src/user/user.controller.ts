/**
 * User Controller — 用户资料 / 收货地址（BFF 透传）
 */
import { Controller, Get, Post, Put, Delete, Param, Body, Req, UseGuards } from '@nestjs/common';
import { JwtAuthGuard } from '../common/guards/jwt-auth.guard.js';
import { UserService } from './user.service.js';
import { ApiResponse } from '../common/dto/api-response.js';
import type { Request } from 'express';
import type { UserProfileBody, AddressBody } from './user.types.js';

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
}
