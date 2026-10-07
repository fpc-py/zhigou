/**
 * Payment Service — 沙箱支付透传
 */
import { Injectable, Logger } from '@nestjs/common';
import { HttpService } from '@nestjs/axios';
import { firstValueFrom, catchError, timeout } from 'rxjs';
import { SERVICES, SERVICE_PATHS } from '../config/service.config.js';
import type { PaymentCreateBody, PaymentCreateResult, MockPayBody } from './payment.types.js';

@Injectable()
export class PaymentService {
  private readonly logger = new Logger(PaymentService.name);

  constructor(private readonly http: HttpService) {}

  async create(userId: string, body: PaymentCreateBody): Promise<PaymentCreateResult | null> {
    const url = `${SERVICES.paymentService.url}${SERVICE_PATHS.paymentCreate}`;
    const resp = await firstValueFrom(
      this.http.post(url, { userId, orderNo: body.orderNo, amount: body.amount }, { headers: { 'x-user-id': userId } }).pipe(
        timeout(SERVICES.paymentService.timeout),
        catchError((err) => {
          this.logger.warn(`payment-service /payment/create 失败: ${err.message}`);
          return Promise.resolve({ data: { data: null } });
        }),
      ),
    );
    return resp.data?.data ?? null;
  }

  async mockPay(body: MockPayBody): Promise<void> {
    const url = `${SERVICES.paymentService.url}${SERVICE_PATHS.paymentMockPay}`;
    // 支付结果必须如实透传：payment-service 403/异常时抛给上层，禁止伪装成功，
    // 否则前端会误显示"支付成功"而订单仍待付款。
    await firstValueFrom(
      this.http.post(url, body).pipe(
        timeout(SERVICES.paymentService.timeout),
      ),
    );
  }
}
