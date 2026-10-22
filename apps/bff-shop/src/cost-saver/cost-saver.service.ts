/**
 * Cost Saver Service — AI 省钱助手透传（C 块 P0）
 * 19 位 id 纪律：透传层不做 JSON 二次序列化，rawBody 字符串直传，避免 JS number 精度丢失（坑 #63 方案）。
 */
import { Injectable, Logger } from '@nestjs/common';
import { HttpService } from '@nestjs/axios';
import { firstValueFrom } from 'rxjs';
import { SERVICES, SERVICE_PATHS } from '../config/service.config.js';

@Injectable()
export class CostSaverService {
  private readonly logger = new Logger(CostSaverService.name);

  constructor(private readonly http: HttpService) {}

  private url(path: string): string {
    return `${SERVICES.productService.url}${path}`;
  }

  async overview(userId: string): Promise<any> {
    try {
      const resp = await firstValueFrom(
        this.http.get(this.url(SERVICE_PATHS.costSaverOverview), { headers: { 'x-user-id': userId } }),
      );
      return resp.data?.data ?? null;
    } catch (err) {
      this.logger.warn(`cost-saver/overview 失败: ${err instanceof Error ? err.message : String(err)}`);
      return null;
    }
  }

  async history(userId: string, skuId: string, days: string): Promise<any> {
    try {
      const resp = await firstValueFrom(
        this.http.get(this.url(SERVICE_PATHS.priceHistory(skuId, Number(days) || 30)), {
          headers: { 'x-user-id': userId },
        }),
      );
      return resp.data?.data ?? null;
    } catch (err) {
      this.logger.warn(`price/history 失败: ${err instanceof Error ? err.message : String(err)}`);
      return null;
    }
  }

  async watch(userId: string, body: { skuId: string | number; targetPrice?: string | number | null }): Promise<any> {
    try {
      const resp = await firstValueFrom(
        this.http.post(this.url(SERVICE_PATHS.priceAlertCreate), body, {
          headers: { 'x-user-id': userId, 'Content-Type': 'application/json' },
        }),
      );
      return resp.data?.data ?? null;
    } catch (err) {
      this.logger.warn(`price/alert 关注失败: ${err instanceof Error ? err.message : String(err)}`);
      throw err;
    }
  }

  async unwatch(userId: string, skuId: string): Promise<any> {
    try {
      const resp = await firstValueFrom(
        this.http.delete(this.url(SERVICE_PATHS.priceAlertDelete(skuId)), {
          headers: { 'x-user-id': userId },
        }),
      );
      return resp.data?.data ?? null;
    } catch (err) {
      this.logger.warn(`price/alert 取消关注失败: ${err instanceof Error ? err.message : String(err)}`);
      throw err;
    }
  }

  async mine(userId: string): Promise<any[]> {
    try {
      const resp = await firstValueFrom(
        this.http.get(this.url(SERVICE_PATHS.priceAlertMine), { headers: { 'x-user-id': userId } }),
      );
      return resp.data?.data ?? [];
    } catch (err) {
      this.logger.warn(`price/alert/mine 失败: ${err instanceof Error ? err.message : String(err)}`);
      return [];
    }
  }

  async notices(userId: string, unreadOnly: boolean): Promise<any[]> {
    try {
      const resp = await firstValueFrom(
        this.http.get(this.url(SERVICE_PATHS.priceNoticeMine(unreadOnly)), { headers: { 'x-user-id': userId } }),
      );
      return resp.data?.data ?? [];
    } catch (err) {
      this.logger.warn(`price/notice/mine 失败: ${err instanceof Error ? err.message : String(err)}`);
      return [];
    }
  }

  async markRead(userId: string, noticeId: string): Promise<any> {
    try {
      const resp = await firstValueFrom(
        this.http.post(this.url(SERVICE_PATHS.priceNoticeRead(noticeId)), null, {
          headers: { 'x-user-id': userId },
        }),
      );
      return resp.data?.data ?? null;
    } catch (err) {
      this.logger.warn(`price/notice/read 失败: ${err instanceof Error ? err.message : String(err)}`);
      throw err;
    }
  }
}
