/**
 * Life Service — 本地生活（POI 门店/商圈 + 服务 SKU + 到店预约）透传
 * 契约同 community：全量 JWT 鉴权，出站注入 X-User-Id；业务失败 HTTP200+code≠200 → HttpException。
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
export class LifeService {
  private readonly logger = new Logger(LifeService.name);
  constructor(private readonly http: HttpService) {}

  private authHeader(userId: string): Record<string, string> {
    return { 'x-user-id': userId };
  }

  /** 门店/商圈分页 */
  async pageStores(userId: string, category: string | undefined, pageNum: number, pageSize: number): Promise<any> {
    const qs = new URLSearchParams({ pageNum: String(pageNum), pageSize: String(pageSize) });
    if (category) qs.set('category', category);
    const url = `${SERVICES.lifeSvc.url}${SERVICE_PATHS.lifePoiPage}?${qs}`;
    const resp = await firstValueFrom(
      this.http.get(url, { headers: this.authHeader(userId) }).pipe(
        timeout(SERVICES.lifeSvc.timeout),
        catchError((err) => {
          this.logger.warn(`life-service /life/poi/page 失败: ${err.message}`);
          return Promise.resolve({ data: { data: [] } });
        }),
      ),
    );
    return unwrapOrThrow(resp, this.logger, 'life/poi/page', []);
  }

  /** 门店详情（含 SKU） */
  async storeDetail(userId: string, id: string): Promise<any | null> {
    const url = `${SERVICES.lifeSvc.url}${SERVICE_PATHS.lifePoiDetail(id)}`;
    const resp = await firstValueFrom(
      this.http.get(url, { headers: this.authHeader(userId) }).pipe(
        timeout(SERVICES.lifeSvc.timeout),
        catchError((err) => {
          this.logger.warn(`life-service /life/poi/${id} 失败: ${err.message}`);
          return Promise.resolve({ data: { data: null } });
        }),
      ),
    );
    return unwrapOrThrow(resp, this.logger, `life/poi/${id}`, null);
  }

  /** 服务 SKU 分页 */
  async pageSkus(userId: string, category: string | undefined, pageNum: number, pageSize: number): Promise<any> {
    const qs = new URLSearchParams({ pageNum: String(pageNum), pageSize: String(pageSize) });
    if (category) qs.set('category', category);
    const url = `${SERVICES.lifeSvc.url}${SERVICE_PATHS.lifeSkuPage}?${qs}`;
    const resp = await firstValueFrom(
      this.http.get(url, { headers: this.authHeader(userId) }).pipe(
        timeout(SERVICES.lifeSvc.timeout),
        catchError((err) => {
          this.logger.warn(`life-service /life/sku/page 失败: ${err.message}`);
          return Promise.resolve({ data: { data: [] } });
        }),
      ),
    );
    return unwrapOrThrow(resp, this.logger, 'life/sku/page', []);
  }

  /** 创建到店/服务预约 */
  async createAppointment(userId: string, body: any): Promise<any | null> {
    const url = `${SERVICES.lifeSvc.url}${SERVICE_PATHS.lifeAppointment}`;
    const resp = await firstValueFrom(
      this.http.post(url, { ...body, userId }, { headers: this.authHeader(userId) }).pipe(
        timeout(SERVICES.lifeSvc.timeout),
        catchError((err) => {
          this.logger.warn(`life-service /life/appointment 失败: ${err.message}`);
          return Promise.resolve({ data: { data: null } });
        }),
      ),
    );
    return unwrapOrThrow(resp, this.logger, 'life/appointment', null);
  }

  /** 我的预约 */
  async myAppointments(userId: string): Promise<any> {
    const url = `${SERVICES.lifeSvc.url}${SERVICE_PATHS.lifeAppointmentMine}?userId=${encodeURIComponent(userId)}`;
    const resp = await firstValueFrom(
      this.http.get(url, { headers: this.authHeader(userId) }).pipe(
        timeout(SERVICES.lifeSvc.timeout),
        catchError((err) => {
          this.logger.warn(`life-service /life/appointment/mine 失败: ${err.message}`);
          return Promise.resolve({ data: { data: [] } });
        }),
      ),
    );
    return unwrapOrThrow(resp, this.logger, 'life/appointment/mine', []);
  }

  /** 取消预约 */
  async cancelAppointment(userId: string, id: string): Promise<any | null> {
    const url = `${SERVICES.lifeSvc.url}${SERVICE_PATHS.lifeAppointmentCancel(id)}`;
    const resp = await firstValueFrom(
      this.http.post(url, { userId }, { headers: this.authHeader(userId) }).pipe(
        timeout(SERVICES.lifeSvc.timeout),
        catchError((err) => {
          this.logger.warn(`life-service /life/appointment/${id}/cancel 失败: ${err.message}`);
          return Promise.resolve({ data: { data: null } });
        }),
      ),
    );
    return unwrapOrThrow(resp, this.logger, `life/appointment/${id}/cancel`, null);
  }
}
