/**
 * Payment Controller — 沙箱支付（BFF 透传）
 */
import { Controller, Post, Body, Req, UseGuards } from '@nestjs/common';
import { JwtAuthGuard } from '../common/guards/jwt-auth.guard.js';
import { PaymentService } from './payment.service.js';
import { ApiResponse } from '../common/dto/api-response.js';
import type { Request } from 'express';
import type { PaymentCreateBody, MockPayBody } from './payment.types.js';

@Controller('payment')
@UseGuards(JwtAuthGuard)
export class PaymentController {
  constructor(private readonly paymentService: PaymentService) {}

  @Post('create')
  async create(@Body() body: PaymentCreateBody, @Req() req: Request) {
    const userId = (req as any).userId as string;
    const data = await this.paymentService.create(userId, body);
    return ApiResponse.ok(data);
  }

  @Post('sandbox/mock-pay')
  async mockPay(@Body() body: MockPayBody) {
    await this.paymentService.mockPay(body);
    return ApiResponse.ok(null);
  }
}
