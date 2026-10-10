/**
 * Marketing Service — 优惠券 / 优惠试算 / 拼团 透传
 * 说明：
 * - marketing-service 全量 JWT 鉴权，BFF 出站统一注入 X-User-Id 头（内网鉴权，见 packages/common JwtAuthFilter）。
 * - 后端业务失败约定：HTTP 200 + body {code!=200, message}。BFF 统一转 HttpException(400, message)，
 *   由全局过滤器输出 {code:400, message}，前端可感知具体业务原因（已开团/已参团/团已满等）。
 */
import { Injectable, Logger, HttpException, HttpStatus } from '@nestjs/common';
import { HttpService } from '@nestjs/axios';
import { firstValueFrom, catchError, timeout } from 'rxjs';
import { SERVICES, SERVICE_PATHS } from '../config/service.config.js';
import type { UserCoupon, DiscountCalculateBody, DiscountCalculateResult } from './marketing.types.js';

/** 解析后端 body：code!=200 视为业务失败，抛 HttpException 透传 message；网络错误降级 */
function unwrapOrThrow<T>(
  resp: { data: { code?: number; message?: string; data?: T } },
  logger: Logger,
  label: string,
  fallback: T,
): T {
  const body = resp.data;
  if (body == null) return fallback;
  if (body.code !== undefined && body.code !== 200) {
    throw new HttpException(body.message || `${label} 业务处理失败`, HttpStatus.BAD_REQUEST);
  }
  return (body.data as T) ?? fallback;
}

@Injectable()
export class MarketingService {
  private readonly logger = new Logger(MarketingService.name);

  constructor(private readonly http: HttpService) {}

  private authHeader(userId: string): Record<string, string> {
    return { 'x-user-id': userId };
  }

  async myCoupons(userId: string, status?: string): Promise<UserCoupon[]> {
    const url = `${SERVICES.marketingSvc.url}${SERVICE_PATHS.userCoupons}?userId=${encodeURIComponent(userId)}${
      status ? `&status=${encodeURIComponent(status)}` : ''
    }`;
    const resp = await firstValueFrom(
      this.http.get(url, { headers: this.authHeader(userId) }).pipe(
        timeout(SERVICES.marketingSvc.timeout),
        catchError((err) => {
          this.logger.warn(`marketing-service /coupon/mine 失败: ${err.message}`);
          return Promise.resolve({ data: { data: [] } });
        }),
      ),
    );
    return unwrapOrThrow(resp, this.logger, 'coupon/mine', []);
  }

  async calculate(userId: string, body: DiscountCalculateBody): Promise<DiscountCalculateResult | null> {
    const url = `${SERVICES.marketingSvc.url}${SERVICE_PATHS.discountCalculate}`;
    const resp = await firstValueFrom(
      this.http.post(url, { ...body, userId }, { headers: this.authHeader(userId) }).pipe(
        timeout(SERVICES.marketingSvc.timeout),
        catchError((err) => {
          this.logger.warn(`marketing-service /discount/calculate 失败: ${err.message}`);
          return Promise.resolve({ data: { data: null } });
        }),
      ),
    );
    return unwrapOrThrow(resp, this.logger, 'discount/calculate', null);
  }

  // ==================== 拼团 ====================

  /** 拼团活动列表（含可加入 OPEN 团单与成团进度） */
  async groupBuyActivities(userId: string): Promise<any[]> {
    const url = `${SERVICES.marketingSvc.url}${SERVICE_PATHS.groupBuyActivities}`;
    const resp = await firstValueFrom(
      this.http.get(url, { headers: this.authHeader(userId) }).pipe(
        timeout(SERVICES.marketingSvc.timeout),
        catchError((err) => {
          this.logger.warn(`marketing-service /group-buy/activities 失败: ${err.message}`);
          return Promise.resolve({ data: { data: [] } });
        }),
      ),
    );
    return unwrapOrThrow(resp, this.logger, 'group-buy/activities', []);
  }

  /** 开团 */
  async groupBuyOpen(userId: string, activityId: number): Promise<any | null> {
    const url = `${SERVICES.marketingSvc.url}${SERVICE_PATHS.groupBuyOpen}`;
    const resp = await firstValueFrom(
      this.http.post(url, { userId, activityId }, { headers: this.authHeader(userId) }).pipe(
        timeout(SERVICES.marketingSvc.timeout),
        catchError((err) => {
          this.logger.warn(`marketing-service /group-buy/open 失败: ${err.message}`);
          return Promise.resolve({ data: { data: null } });
        }),
      ),
    );
    return unwrapOrThrow(resp, this.logger, 'group-buy/open', null);
  }

  /** 参团 */
  async groupBuyJoin(userId: string, groupId: number): Promise<any | null> {
    const url = `${SERVICES.marketingSvc.url}${SERVICE_PATHS.groupBuyJoin}`;
    const resp = await firstValueFrom(
      this.http.post(url, { userId, groupId }, { headers: this.authHeader(userId) }).pipe(
        timeout(SERVICES.marketingSvc.timeout),
        catchError((err) => {
          this.logger.warn(`marketing-service /group-buy/join 失败: ${err.message}`);
          return Promise.resolve({ data: { data: null } });
        }),
      ),
    );
    return unwrapOrThrow(resp, this.logger, 'group-buy/join', null);
  }

  /** 我的团单 */
  async groupBuyMine(userId: string): Promise<any[]> {
    const url = `${SERVICES.marketingSvc.url}${SERVICE_PATHS.groupBuyMine}?userId=${encodeURIComponent(userId)}`;
    const resp = await firstValueFrom(
      this.http.get(url, { headers: this.authHeader(userId) }).pipe(
        timeout(SERVICES.marketingSvc.timeout),
        catchError((err) => {
          this.logger.warn(`marketing-service /group-buy/mine 失败: ${err.message}`);
          return Promise.resolve({ data: { data: [] } });
        }),
      ),
    );
    return unwrapOrThrow(resp, this.logger, 'group-buy/mine', []);
  }

  /** 团单详情 */
  async groupBuyDetail(userId: string, groupId: string): Promise<any | null> {
    const url = `${SERVICES.marketingSvc.url}${SERVICE_PATHS.groupBuyDetail(groupId)}`;
    const resp = await firstValueFrom(
      this.http.get(url, { headers: this.authHeader(userId) }).pipe(
        timeout(SERVICES.marketingSvc.timeout),
        catchError((err) => {
          this.logger.warn(`marketing-service /group-buy/group/${groupId} 失败: ${err.message}`);
          return Promise.resolve({ data: { data: null } });
        }),
      ),
    );
    return unwrapOrThrow(resp, this.logger, `group-buy/group/${groupId}`, null);
  }
}
