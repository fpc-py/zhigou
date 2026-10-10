/**
 * Wallet Service — 会员钱包透传（账户/沙箱充值/流水/会员等级）
 * 契约同 closet：全量 JWT 鉴权，出站注入 X-User-Id；业务失败 HTTP200+code≠200 → HttpException。
 */
import { Injectable, Logger, HttpException, HttpStatus } from '@nestjs/common';
import { HttpService } from '@nestjs/axios';
import { firstValueFrom, catchError, timeout } from 'rxjs';
import { SERVICES, SERVICE_PATHS } from '../config/service.config.js';

function unwrapOrThrow<T>(resp: { data: { code?: number; message?: string; data?: T } }, logger: Logger, label: string, fallback: T): T {
  const body = resp.data;
  if (body == null) return fallback;
  if (body.code !== undefined && body.code !== 200) {
    throw new HttpException(body.message || `${label} 业务处理失败`, HttpStatus.BAD_REQUEST);
  }
  return (body.data as T) ?? fallback;
}

@Injectable()
export class WalletService {
  private readonly logger = new Logger(WalletService.name);
  constructor(private readonly http: HttpService) {}

  private authHeader(userId: string): Record<string, string> {
    return { 'x-user-id': userId };
  }

  /** 我的钱包账户 */
  async myAccount(userId: string): Promise<any | null> {
    const url = `${SERVICES.walletSvc.url}${SERVICE_PATHS.walletAccount}?userId=${encodeURIComponent(userId)}`;
    const resp = await firstValueFrom(
      this.http.get(url, { headers: this.authHeader(userId) }).pipe(
        timeout(SERVICES.walletSvc.timeout),
        catchError((err) => {
          this.logger.warn(`wallet-service /wallet/account 失败: ${err.message}`);
          return Promise.resolve({ data: { data: null } });
        }),
      ),
    );
    return unwrapOrThrow(resp, this.logger, 'wallet/account', null);
  }

  /** 沙箱充值 */
  async recharge(userId: string, body: { amountFen: number; bizNo?: string; remark?: string }): Promise<any | null> {
    const url = `${SERVICES.walletSvc.url}${SERVICE_PATHS.walletRecharge}`;
    const resp = await firstValueFrom(
      this.http.post(url, { ...body, userId }, { headers: this.authHeader(userId) }).pipe(
        timeout(SERVICES.walletSvc.timeout),
        catchError((err) => {
          this.logger.warn(`wallet-service /wallet/recharge 失败: ${err.message}`);
          return Promise.resolve({ data: { data: null } });
        }),
      ),
    );
    return unwrapOrThrow(resp, this.logger, 'wallet/recharge', null);
  }

  /** 流水分页 */
  async transactions(userId: string, pageNum = 1, pageSize = 20): Promise<any> {
    const url = `${SERVICES.walletSvc.url}${SERVICE_PATHS.walletTransactions}?userId=${encodeURIComponent(userId)}&pageNum=${pageNum}&pageSize=${pageSize}`;
    const resp = await firstValueFrom(
      this.http.get(url, { headers: this.authHeader(userId) }).pipe(
        timeout(SERVICES.walletSvc.timeout),
        catchError((err) => {
          this.logger.warn(`wallet-service /wallet/transactions 失败: ${err.message}`);
          return Promise.resolve({ data: { data: [] } });
        }),
      ),
    );
    return unwrapOrThrow(resp, this.logger, 'wallet/transactions', []);
  }

  /** 会员等级与权益 */
  async memberLevel(userId: string): Promise<any | null> {
    const url = `${SERVICES.walletSvc.url}${SERVICE_PATHS.walletLevel}?userId=${encodeURIComponent(userId)}`;
    const resp = await firstValueFrom(
      this.http.get(url, { headers: this.authHeader(userId) }).pipe(
        timeout(SERVICES.walletSvc.timeout),
        catchError((err) => {
          this.logger.warn(`wallet-service /wallet/level 失败: ${err.message}`);
          return Promise.resolve({ data: { data: null } });
        }),
      ),
    );
    return unwrapOrThrow(resp, this.logger, 'wallet/level', null);
  }

  /** 开通/续费会员（沙箱扣余额） */
  async subscribe(userId: string, level: string): Promise<any | null> {
    const url = `${SERVICES.walletSvc.url}${SERVICE_PATHS.walletSubscribe}`;
    const resp = await firstValueFrom(
      this.http.post(url, { userId, level }, { headers: this.authHeader(userId) }).pipe(
        timeout(SERVICES.walletSvc.timeout),
        catchError((err) => {
          this.logger.warn(`wallet-service /wallet/subscribe 失败: ${err.message}`);
          return Promise.resolve({ data: { data: null } });
        }),
      ),
    );
    return unwrapOrThrow(resp, this.logger, 'wallet/subscribe', null);
  }

  /** 当前订阅状态 */
  async subscription(userId: string): Promise<any | null> {
    const url = `${SERVICES.walletSvc.url}${SERVICE_PATHS.walletSubscription}?userId=${encodeURIComponent(userId)}`;
    const resp = await firstValueFrom(
      this.http.get(url, { headers: this.authHeader(userId) }).pipe(
        timeout(SERVICES.walletSvc.timeout),
        catchError((err) => {
          this.logger.warn(`wallet-service /wallet/subscription 失败: ${err.message}`);
          return Promise.resolve({ data: { data: null } });
        }),
      ),
    );
    return unwrapOrThrow(resp, this.logger, 'wallet/subscription', null);
  }
}
