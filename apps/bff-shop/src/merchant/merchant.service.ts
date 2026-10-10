/**
 * Merchant Service — 商家经营（BFF 透传 order-service 经营概览；演示：单商家市场 = 平台聚合）
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
export class MerchantService {
  private readonly logger = new Logger(MerchantService.name);
  constructor(private readonly http: HttpService) {}

  /** 经营概览（平台聚合，商家视角演示口径） */
  async overview(): Promise<any | null> {
    const url = `${SERVICES.orderService.url}${SERVICE_PATHS.orderStatsOverview}`;
    const resp = await firstValueFrom(
      this.http.get(url).pipe(
        timeout(SERVICES.orderService.timeout),
        catchError((err) => {
          this.logger.warn(`order-service /order/stats/overview 失败: ${err.message}`);
          return Promise.resolve({ data: { data: null } });
        }),
      ),
    );
    return unwrapOrThrow(resp, this.logger, 'merchant/overview', null);
  }

  /** 履约异常：PAID 待发货订单 + SKU 明细（供缺货/卡单预警，演示口径） */
  async fulfillment(): Promise<any[] | null> {
    const resp = await firstValueFrom(
      this.http.get(`${SERVICES.orderService.url}${SERVICE_PATHS.orderPendingFulfillment}`).pipe(
        timeout(SERVICES.orderService.timeout),
        catchError((err) => {
          this.logger.warn(`order-service /order/stats/pending-fulfillment 失败: ${err.message}`);
          return Promise.resolve({ data: { data: null } });
        }),
      ),
    );
    return unwrapOrThrow(resp, this.logger, 'merchant/fulfillment', []);
  }

  /** 经营预警：低库存 SKU + 近期差评（演示：单商家市场 = 平台聚合口径） */
  async warnings(): Promise<{ lowStock: any[]; negative: any[] } | null> {
    const low = (await firstValueFrom(
      this.http.get(`${SERVICES.inventorySvc.url}${SERVICE_PATHS.inventoryLowStock}`).pipe(
        timeout(SERVICES.inventorySvc.timeout),
        catchError((err) => {
          this.logger.warn(`inventory-service /inventory/low-stock 失败: ${err.message}`);
          return Promise.resolve({ data: { data: [] } });
        }),
      ),
    )) as any;
    const neg = (await firstValueFrom(
      this.http
        .get(`${SERVICES.productService.url}${SERVICE_PATHS.productReviewNegative}`, {
          headers: { 'X-User-Id': '2107757313435291648' },
        })
        .pipe(
          timeout(SERVICES.productService.timeout),
          catchError((err) => {
            this.logger.warn(`product-service /product/review/negative 失败: ${err.message}`);
            return Promise.resolve({ data: { data: [] } });
          }),
        ),
    )) as any;
    return {
      lowStock: unwrapOrThrow(low, this.logger, 'merchant/warnings/lowStock', []),
      negative: unwrapOrThrow(neg, this.logger, 'merchant/warnings/negative', []),
    };
  }
}
