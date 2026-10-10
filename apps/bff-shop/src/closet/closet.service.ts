/**
 * Closet Service — 智能衣橱 + 家居管理透传
 * 契约同 life：全量 JWT 鉴权，出站注入 X-User-Id；业务失败 HTTP200+code≠200 → HttpException。
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
export class ClosetService {
  private readonly logger = new Logger(ClosetService.name);
  constructor(private readonly http: HttpService) {}

  private authHeader(userId: string): Record<string, string> {
    return { 'x-user-id': userId };
  }

  /** 我的衣物 */
  async myItems(userId: string, category?: string, season?: string): Promise<any> {
    const qs = new URLSearchParams({ userId });
    if (category) qs.set('category', category);
    if (season) qs.set('season', season);
    const url = `${SERVICES.closetSvc.url}${SERVICE_PATHS.closetItems}?${qs}`;
    const resp = await firstValueFrom(
      this.http.get(url, { headers: this.authHeader(userId) }).pipe(
        timeout(SERVICES.closetSvc.timeout),
        catchError((err) => {
          this.logger.warn(`closet-service /closet/items 失败: ${err.message}`);
          return Promise.resolve({ data: { data: [] } });
        }),
      ),
    );
    return unwrapOrThrow(resp, this.logger, 'closet/items', []);
  }

  /** 添加衣物 */
  async addItem(userId: string, body: any): Promise<any | null> {
    const url = `${SERVICES.closetSvc.url}${SERVICE_PATHS.closetItem}`;
    const resp = await firstValueFrom(
      this.http.post(url, { ...body, userId }, { headers: this.authHeader(userId) }).pipe(
        timeout(SERVICES.closetSvc.timeout),
        catchError((err) => {
          this.logger.warn(`closet-service /closet/item 失败: ${err.message}`);
          return Promise.resolve({ data: { data: null } });
        }),
      ),
    );
    return unwrapOrThrow(resp, this.logger, 'closet/item', null);
  }

  /** 穿着打卡 */
  async wear(userId: string, id: string): Promise<any | null> {
    const url = `${SERVICES.closetSvc.url}${SERVICE_PATHS.closetItemWear(id)}`;
    const resp = await firstValueFrom(
      this.http.post(url, { userId }, { headers: this.authHeader(userId) }).pipe(
        timeout(SERVICES.closetSvc.timeout),
        catchError((err) => {
          this.logger.warn(`closet-service /closet/item/${id}/wear 失败: ${err.message}`);
          return Promise.resolve({ data: { data: null } });
        }),
      ),
    );
    return unwrapOrThrow(resp, this.logger, `closet/item/${id}/wear`, null);
  }

  /** 删除衣物 */
  async removeItem(userId: string, id: string): Promise<any | null> {
    const url = `${SERVICES.closetSvc.url}${SERVICE_PATHS.closetItemDelete(id)}?userId=${encodeURIComponent(userId)}`;
    const resp = await firstValueFrom(
      this.http.delete(url, { headers: this.authHeader(userId) }).pipe(
        timeout(SERVICES.closetSvc.timeout),
        catchError((err) => {
          this.logger.warn(`closet-service DELETE /closet/item/${id} 失败: ${err.message}`);
          return Promise.resolve({ data: { data: null } });
        }),
      ),
    );
    return unwrapOrThrow(resp, this.logger, `closet/item/${id}`, null);
  }

  /** 穿搭推荐 */
  async recommendOutfit(userId: string, occasion?: string): Promise<any | null> {
    const qs = new URLSearchParams({ userId });
    if (occasion) qs.set('occasion', occasion);
    const url = `${SERVICES.closetSvc.url}${SERVICE_PATHS.closetOutfitRecommend}?${qs}`;
    const resp = await firstValueFrom(
      this.http.get(url, { headers: this.authHeader(userId) }).pipe(
        timeout(SERVICES.closetSvc.timeout),
        catchError((err) => {
          this.logger.warn(`closet-service /closet/outfit/recommend 失败: ${err.message}`);
          return Promise.resolve({ data: { data: null } });
        }),
      ),
    );
    return unwrapOrThrow(resp, this.logger, 'closet/outfit/recommend', null);
  }

  /** 家居盘点 */
  async myAssets(userId: string, category?: string): Promise<any> {
    const qs = new URLSearchParams({ userId });
    if (category) qs.set('category', category);
    const url = `${SERVICES.closetSvc.url}${SERVICE_PATHS.closetHomeList}?${qs}`;
    const resp = await firstValueFrom(
      this.http.get(url, { headers: this.authHeader(userId) }).pipe(
        timeout(SERVICES.closetSvc.timeout),
        catchError((err) => {
          this.logger.warn(`closet-service /closet/home/list 失败: ${err.message}`);
          return Promise.resolve({ data: { data: [] } });
        }),
      ),
    );
    return unwrapOrThrow(resp, this.logger, 'closet/home/list', []);
  }

  /** 添加家居物品 */
  async addAsset(userId: string, body: any): Promise<any | null> {
    const url = `${SERVICES.closetSvc.url}${SERVICE_PATHS.closetHomeItem}`;
    const resp = await firstValueFrom(
      this.http.post(url, { ...body, userId }, { headers: this.authHeader(userId) }).pipe(
        timeout(SERVICES.closetSvc.timeout),
        catchError((err) => {
          this.logger.warn(`closet-service /closet/home/item 失败: ${err.message}`);
          return Promise.resolve({ data: { data: null } });
        }),
      ),
    );
    return unwrapOrThrow(resp, this.logger, 'closet/home/item', null);
  }

  /** 补货清单 */
  async replenishList(userId: string): Promise<any> {
    const url = `${SERVICES.closetSvc.url}${SERVICE_PATHS.closetHomeReplenish}?userId=${encodeURIComponent(userId)}`;
    const resp = await firstValueFrom(
      this.http.get(url, { headers: this.authHeader(userId) }).pipe(
        timeout(SERVICES.closetSvc.timeout),
        catchError((err) => {
          this.logger.warn(`closet-service /closet/home/replenish 失败: ${err.message}`);
          return Promise.resolve({ data: { data: [] } });
        }),
      ),
    );
    return unwrapOrThrow(resp, this.logger, 'closet/home/replenish', []);
  }
}
