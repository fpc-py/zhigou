/**
 * Aftersale Service — 售后透传
 * aftersale-service 的查询/商家操作均需 X-User-Id 头（内网鉴权），BFF 统一从 JWT 注入。
 */
import { Injectable, Logger } from '@nestjs/common';
import { HttpService } from '@nestjs/axios';
import { firstValueFrom, catchError, timeout } from 'rxjs';
import { SERVICES } from '../config/service.config.js';
import type { AftersaleOrder, ApplyBody } from './aftersale.types.js';

@Injectable()
export class AftersaleService {
  private readonly logger = new Logger(AftersaleService.name);
  private readonly base = SERVICES.aftersaleSvc.url;
  private readonly t = SERVICES.aftersaleSvc.timeout;

  constructor(private readonly http: HttpService) {}

  private authHeader(userId: string): Record<string, string> {
    return { 'x-user-id': userId };
  }

  async apply(userId: string, body: ApplyBody): Promise<AftersaleOrder | null> {
    const resp = await firstValueFrom(
      this.http.post(`${this.base}/aftersale/apply`, body, { headers: this.authHeader(userId) }).pipe(
        timeout(this.t),
        catchError((err) => {
          this.logger.warn(`aftersale-service /aftersale/apply 失败: ${err.message}`);
          return Promise.resolve({ data: { data: null } });
        }),
      ),
    );
    return resp.data?.data ?? null;
  }

  async mine(userId: string): Promise<AftersaleOrder[]> {
    const resp = await firstValueFrom(
      this.http.get(`${this.base}/aftersale/mine?userId=${encodeURIComponent(userId)}`, {
        headers: this.authHeader(userId),
      }).pipe(
        timeout(this.t),
        catchError((err) => {
          this.logger.warn(`aftersale-service /aftersale/mine 失败: ${err.message}`);
          return Promise.resolve({ data: { data: [] } });
        }),
      ),
    );
    return resp.data?.data ?? [];
  }

  async detail(userId: string, no: string): Promise<AftersaleOrder | null> {
    const resp = await firstValueFrom(
      this.http.get(`${this.base}/aftersale/${encodeURIComponent(no)}`, {
        headers: this.authHeader(userId),
      }).pipe(
        timeout(this.t),
        catchError((err) => {
          this.logger.warn(`aftersale-service /aftersale/${no} 失败: ${err.message}`);
          return Promise.resolve({ data: { data: null } });
        }),
      ),
    );
    return resp.data?.data ?? null;
  }

  async cancel(userId: string, no: string): Promise<void> {
    await firstValueFrom(
      this.http.post(`${this.base}/aftersale/${encodeURIComponent(no)}/cancel`, null, {
        headers: this.authHeader(userId),
      }).pipe(
        timeout(this.t),
        catchError((err) => {
          this.logger.warn(`aftersale-service /aftersale/${no}/cancel 失败: ${err.message}`);
          return Promise.resolve({ data: null });
        }),
      ),
    );
  }
}
