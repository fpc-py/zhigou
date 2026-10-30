/**
 * Aftersale Service — 售后透传
 * aftersale-service 的查询/商家操作均需 X-User-Id 头（内网鉴权），BFF 统一从 JWT 注入。
 * 修复 #88：下游业务错误（HTTP 200 + body.code!=200 / HTTP >=400）显式透传，
 * 不再吞成 success-null，前端可提示真实失败原因。
 */
import { Injectable, Logger, HttpException, HttpStatus } from '@nestjs/common';
import { HttpService } from '@nestjs/axios';
import { firstValueFrom, catchError, timeout, of } from 'rxjs';
import { SERVICES } from '../config/service.config.js';
import type { AftersaleOrder } from './aftersale.types.js';

/** 下游业务错误（HTTP 200 + body.code!=200）→ 按业务码抛 HttpException，避免吞成 success-null */
function unwrapBizOrThrow<T>(
  resp: { data: { code?: number; message?: string; data?: T | null } },
  logger: Logger,
  label: string,
  fallback: T,
): T {
  const body = resp.data;
  if (body == null) return fallback;
  if (body.code !== undefined && body.code !== 200) {
    const status = typeof body.code === 'number' && body.code >= 400 && body.code < 600 ? body.code : HttpStatus.BAD_REQUEST;
    logger.warn(`${label} 业务失败: code=${body.code} message=${body.message}`);
    throw new HttpException(body.message || `${label} 业务处理失败`, status);
  }
  return (body.data as T) ?? fallback;
}

/** HTTP 层错误（下游 4xx/5xx）→ 按原状态码抛，透传鉴权/越权等语义 */
function throwHttpOrFallback(err: any, logger: Logger, label: string) {
  const st = err.response?.status;
  if (st && st >= 400) {
    const msg = err.response?.data?.message || err.response?.data?.error || `${label} 失败`;
    throw new HttpException(msg, st);
  }
  logger.warn(`${label} 失败: ${err.message}`);
  return of({ data: { data: null } });
}

@Injectable()
export class AftersaleService {
  private readonly logger = new Logger(AftersaleService.name);
  private readonly base = SERVICES.aftersaleSvc.url;
  private readonly t = SERVICES.aftersaleSvc.timeout;

  constructor(private readonly http: HttpService) {}

  private authHeader(userId: string): Record<string, string> {
    return { 'x-user-id': userId };
  }

  /** 申请售后：坑 #63 精度修复——rawBody 字符串透传（orderId/skuId 19 位防 JS 精度丢失） */
  async apply(userId: string, rawBody: string): Promise<AftersaleOrder | null> {
    const resp = await firstValueFrom(
      this.http.post(`${this.base}/aftersale/apply`, rawBody, { headers: { 'x-user-id': userId, 'content-type': 'application/json' } }).pipe(
        timeout(this.t),
        catchError((err) => throwHttpOrFallback(err, this.logger, 'aftersale-service /aftersale/apply')),
      ),
    );
    return unwrapBizOrThrow(resp, this.logger, 'aftersale/apply', null);
  }

  async mine(userId: string): Promise<AftersaleOrder[]> {
    const resp = await firstValueFrom(
      this.http.get(`${this.base}/aftersale/mine?userId=${encodeURIComponent(userId)}`, {
        headers: this.authHeader(userId),
      }).pipe(
        timeout(this.t),
        catchError((err) => throwHttpOrFallback(err, this.logger, 'aftersale-service /aftersale/mine')),
      ),
    );
    return unwrapBizOrThrow<AftersaleOrder[]>(resp, this.logger, 'aftersale/mine', []);
  }

  async detail(userId: string, no: string): Promise<AftersaleOrder | null> {
    const resp = await firstValueFrom(
      this.http.get(`${this.base}/aftersale/${encodeURIComponent(no)}`, {
        headers: this.authHeader(userId),
      }).pipe(
        timeout(this.t),
        catchError((err) => throwHttpOrFallback(err, this.logger, `aftersale-service /aftersale/${no}`)),
      ),
    );
    return unwrapBizOrThrow(resp, this.logger, `aftersale/${no}`, null);
  }

  async cancel(userId: string, no: string): Promise<void> {
    const resp = await firstValueFrom(
      this.http.post(`${this.base}/aftersale/${encodeURIComponent(no)}/cancel`, null, {
        headers: this.authHeader(userId),
      }).pipe(
        timeout(this.t),
        catchError((err) => throwHttpOrFallback(err, this.logger, `aftersale-service /aftersale/${no}/cancel`)),
      ),
    );
    unwrapBizOrThrow(resp, this.logger, `aftersale/${no}/cancel`, undefined);
  }

  /** 商家售后工作台：待处理列表 + 全状态统计（status 可选，默认 APPLYING） */
  async merchantPending(userId: string, status?: string): Promise<any | null> {
    const q = status ? `?status=${encodeURIComponent(status)}` : '';
    const resp = await firstValueFrom(
      this.http.get(`${this.base}/aftersale/merchant/pending${q}`, {
        headers: this.authHeader(userId),
      }).pipe(
        timeout(this.t),
        catchError((err) => throwHttpOrFallback(err, this.logger, 'aftersale-service /aftersale/merchant/pending')),
      ),
    );
    return unwrapBizOrThrow(resp, this.logger, 'aftersale/merchant/pending', null);
  }

  /** 商家同意退款（售后单 -> SELLER_APPROVED） */
  async approve(userId: string, no: string): Promise<void> {
    const resp = await firstValueFrom(
      this.http.post(`${this.base}/aftersale/${encodeURIComponent(no)}/approve`, null, {
        headers: this.authHeader(userId),
      }).pipe(
        timeout(this.t),
        catchError((err) => throwHttpOrFallback(err, this.logger, `aftersale-service /aftersale/${no}/approve`)),
      ),
    );
    unwrapBizOrThrow(resp, this.logger, `aftersale/${no}/approve`, undefined);
  }

  /** 商家拒绝售后：rawBody 字符串透传（rejectReason） */
  async reject(userId: string, no: string, rawBody: string): Promise<void> {
    const resp = await firstValueFrom(
      this.http.post(`${this.base}/aftersale/${encodeURIComponent(no)}/reject`, rawBody, {
        headers: { 'x-user-id': userId, 'content-type': 'application/json' },
      }).pipe(
        timeout(this.t),
        catchError((err) => throwHttpOrFallback(err, this.logger, `aftersale-service /aftersale/${no}/reject`)),
      ),
    );
    unwrapBizOrThrow(resp, this.logger, `aftersale/${no}/reject`, undefined);
  }

  /** 质保提醒：30 天内到期 / 已过期 / 正常 */
  async warrantyAlerts(userId: string, days = 30): Promise<any[] | null> {
    const resp = await firstValueFrom(
      this.http.get(`${this.base}/aftersale/warranty/alerts?userId=${encodeURIComponent(userId)}&days=${days}`, {
        headers: this.authHeader(userId),
      }).pipe(
        timeout(this.t),
        catchError((err) => throwHttpOrFallback(err, this.logger, 'aftersale-service /aftersale/warranty/alerts')),
      ),
    );
    return unwrapBizOrThrow(resp, this.logger, 'aftersale/warranty/alerts', null);
  }

  /** 创建维修预约（演示口径 PENDING，正式版需商家确认排期）——rawBody 字符串透传 */
  async createRepairAppointment(userId: string, rawBody: string): Promise<any | null> {
    const resp = await firstValueFrom(
      this.http.post(`${this.base}/aftersale/repair/appointment?userId=${encodeURIComponent(userId)}`, rawBody, {
        headers: { 'x-user-id': userId, 'content-type': 'application/json' },
      }).pipe(
        timeout(this.t),
        catchError((err) => throwHttpOrFallback(err, this.logger, 'aftersale-service /aftersale/repair/appointment')),
      ),
    );
    return unwrapBizOrThrow(resp, this.logger, 'aftersale/repair/appointment', null);
  }

  /** 我的维修预约 */
  async repairAppointments(userId: string): Promise<any[] | null> {
    const resp = await firstValueFrom(
      this.http.get(`${this.base}/aftersale/repair/appointments?userId=${encodeURIComponent(userId)}`, {
        headers: this.authHeader(userId),
      }).pipe(
        timeout(this.t),
        catchError((err) => throwHttpOrFallback(err, this.logger, 'aftersale-service /aftersale/repair/appointments')),
      ),
    );
    return unwrapBizOrThrow(resp, this.logger, 'aftersale/repair/appointments', null);
  }
}