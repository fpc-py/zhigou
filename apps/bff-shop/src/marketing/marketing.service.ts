/**
 * Marketing Service — 优惠券 / 优惠试算透传
 */
import { Injectable, Logger } from '@nestjs/common';
import { HttpService } from '@nestjs/axios';
import { firstValueFrom, catchError, timeout } from 'rxjs';
import { SERVICES, SERVICE_PATHS } from '../config/service.config.js';
import type { UserCoupon, DiscountCalculateBody, DiscountCalculateResult } from './marketing.types.js';

@Injectable()
export class MarketingService {
  private readonly logger = new Logger(MarketingService.name);

  constructor(private readonly http: HttpService) {}

  async myCoupons(userId: string, status?: string): Promise<UserCoupon[]> {
    const url = `${SERVICES.marketingSvc.url}${SERVICE_PATHS.userCoupons}?userId=${encodeURIComponent(userId)}${
      status ? `&status=${encodeURIComponent(status)}` : ''
    }`;
    const resp = await firstValueFrom(
      this.http.get(url).pipe(
        timeout(SERVICES.marketingSvc.timeout),
        catchError((err) => {
          this.logger.warn(`marketing-service /coupon/mine 失败: ${err.message}`);
          return Promise.resolve({ data: { data: [] } });
        }),
      ),
    );
    return resp.data?.data ?? [];
  }

  async calculate(userId: string, body: DiscountCalculateBody): Promise<DiscountCalculateResult | null> {
    const url = `${SERVICES.marketingSvc.url}${SERVICE_PATHS.discountCalculate}`;
    const resp = await firstValueFrom(
      this.http.post(url, { ...body, userId }).pipe(
        timeout(SERVICES.marketingSvc.timeout),
        catchError((err) => {
          this.logger.warn(`marketing-service /discount/calculate 失败: ${err.message}`);
          return Promise.resolve({ data: { data: null } });
        }),
      ),
    );
    return resp.data?.data ?? null;
  }
}
