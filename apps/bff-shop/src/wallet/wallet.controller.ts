/**
 * Wallet Controller — 会员钱包（BFF 透传，全链路登录态由 JwtAuthGuard 保障）
 */
import { Controller, Get, Post, Query, Body, Req, UseGuards } from '@nestjs/common';
import { JwtAuthGuard } from '../common/guards/jwt-auth.guard.js';
import { WalletService } from './wallet.service.js';
import { ApiResponse } from '../common/dto/api-response.js';
import type { Request } from 'express';

@Controller()
@UseGuards(JwtAuthGuard)
export class WalletController {
  constructor(private readonly wallet: WalletService) {}

  private uid(req: Request): string {
    return (req as any).userId as string;
  }

  /** 我的钱包账户 */
  @Get('wallet/account')
  async account(@Req() req: Request) {
    const data = await this.wallet.myAccount(this.uid(req));
    return ApiResponse.ok(data);
  }

  /** 沙箱充值 */
  @Post('wallet/recharge')
  async recharge(@Body() body: { amountFen: number; bizNo?: string; remark?: string }, @Req() req: Request) {
    const data = await this.wallet.recharge(this.uid(req), body);
    return ApiResponse.ok(data);
  }

  /** 流水分页 */
  @Get('wallet/transactions')
  async transactions(@Query('pageNum') pageNum: string, @Query('pageSize') pageSize: string, @Req() req: Request) {
    const data = await this.wallet.transactions(this.uid(req), pageNum ? Number(pageNum) : 1, pageSize ? Number(pageSize) : 20);
    return ApiResponse.ok(data);
  }

  /** 会员等级与权益 */
  @Get('wallet/level')
  async level(@Req() req: Request) {
    const data = await this.wallet.memberLevel(this.uid(req));
    return ApiResponse.ok(data);
  }
}
