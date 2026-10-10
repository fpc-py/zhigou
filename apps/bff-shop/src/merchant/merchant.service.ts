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

  /** 最近补货记录（供应链补货中心，演示口径） */
  async replenishRecords(limit = 10): Promise<any[] | null> {
    const resp = await firstValueFrom(
      this.http.get(`${SERVICES.inventorySvc.url}${SERVICE_PATHS.inventoryReplenishRecords}?limit=${limit}`).pipe(
        timeout(SERVICES.inventorySvc.timeout),
        catchError((err) => {
          this.logger.warn(`inventory-service /inventory/replenish/records 失败: ${err.message}`);
          return Promise.resolve({ data: { data: [] } });
        }),
      ),
    );
    return unwrapOrThrow(resp, this.logger, 'merchant/supply/records', []);
  }

  /** 自动补货：低库存 SKU 补到 targetQty（演示口径直接改库存） */
  async autoReplenish(threshold = 10, targetQty = 50): Promise<any[] | null> {
    const resp = await firstValueFrom(
      this.http
        .post(`${SERVICES.inventorySvc.url}${SERVICE_PATHS.inventoryAutoReplenish}`, {
          threshold,
          targetQty,
        })
        .pipe(
          timeout(SERVICES.inventorySvc.timeout),
          catchError((err) => {
            this.logger.warn(`inventory-service /inventory/auto-replenish 失败: ${err.message}`);
            return Promise.resolve({ data: { data: [] } });
          }),
        ),
    );
    return unwrapOrThrow(resp, this.logger, 'merchant/supply/auto-replenish', []);
  }

  /** 异常订单自动处理：对待发货订单写处理动作（SPLIT/DELAY/OFF_SHELF/REPLENISH，演示口径） */
  async fulfillmentAction(action: string, orderIds: string[], reason?: string): Promise<number | null> {
    // orderId 为 19 位 Snowflake，禁止 Number() 化（超 JS 安全整数会丢精度，CLAUDE.md 红线）；后端 List<Number> 可无损反序列化字符串
    const resp = await firstValueFrom(
      this.http
        .post(`${SERVICES.orderService.url}${SERVICE_PATHS.orderFulfillmentAction}`, {
          action,
          orderIds,
          reason,
        })
        .pipe(
          timeout(SERVICES.orderService.timeout),
          catchError((err) => {
            this.logger.warn(`order-service /order/fulfillment/action 失败: ${err.message}`);
            return Promise.resolve({ data: { data: 0 } });
          }),
        ),
    );
    return unwrapOrThrow(resp, this.logger, 'merchant/fulfillment/action', 0);
  }

  /** 最近异常订单处理记录 */
  async fulfillmentActions(limit = 10): Promise<any[] | null> {
    const resp = await firstValueFrom(
      this.http.get(`${SERVICES.orderService.url}${SERVICE_PATHS.orderFulfillmentActions}?limit=${limit}`).pipe(
        timeout(SERVICES.orderService.timeout),
        catchError((err) => {
          this.logger.warn(`order-service /order/fulfillment/actions 失败: ${err.message}`);
          return Promise.resolve({ data: { data: [] } });
        }),
      ),
    );
    return unwrapOrThrow(resp, this.logger, 'merchant/fulfillment/actions', []);
  }

  /** 手动补货：指定 SKU 增加库存（演示口径） */
  async manualReplenish(skuId: number, addQty: number, remark?: string): Promise<any | null> {
    const resp = await firstValueFrom(
      this.http
        .post(`${SERVICES.inventorySvc.url}${SERVICE_PATHS.inventoryReplenish}`, {
          skuId,
          addQty,
          remark,
        })
        .pipe(
          timeout(SERVICES.inventorySvc.timeout),
          catchError((err) => {
            this.logger.warn(`inventory-service /inventory/replenish 失败: ${err.message}`);
            return Promise.resolve({ data: { data: null } });
          }),
        ),
    );
    return unwrapOrThrow(resp, this.logger, 'merchant/supply/replenish', null);
  }
  /** 销量预测（7/30 天，演示口径：近 7 日日均×天数×(1+趋势)） */
  async forecast(days = 7): Promise<any[] | null> {
    const resp = await firstValueFrom(
      this.http.get(`${SERVICES.productService.url}${SERVICE_PATHS.merchantForecast}?days=${days}`).pipe(
        timeout(SERVICES.productService.timeout),
        catchError((err) => {
          this.logger.warn(`product-service /product/merchant/forecast 失败: ${err.message}`);
          return Promise.resolve({ data: { data: null } });
        }),
      ),
    );
    return unwrapOrThrow(resp, this.logger, 'merchant/forecast', null);
  }

  /** 智能选品（热度/库存/趋势，演示口径） */
  async selection(): Promise<any[] | null> {
    const resp = await firstValueFrom(
      this.http.get(`${SERVICES.productService.url}${SERVICE_PATHS.merchantSelection}`).pipe(
        timeout(SERVICES.productService.timeout),
        catchError((err) => {
          this.logger.warn(`product-service /product/merchant/selection 失败: ${err.message}`);
          return Promise.resolve({ data: { data: null } });
        }),
      ),
    );
    return unwrapOrThrow(resp, this.logger, 'merchant/selection', null);
  }
}
