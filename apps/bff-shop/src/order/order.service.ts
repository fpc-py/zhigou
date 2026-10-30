/**
 * Order Service — 订单透传
 * 注：order-service 部分接口 userId 由调用方传入（body/query），BFF 从 JWT 提取注入。
 */
import { Injectable, Logger, HttpException, HttpStatus } from '@nestjs/common';
import { HttpService } from '@nestjs/axios';
import { firstValueFrom, catchError, timeout, TimeoutError } from 'rxjs';
import { SERVICES, SERVICE_PATHS } from '../config/service.config.js';
import type { CreateOrderBody, OrderDetail } from './order.types.js';

/** 下游业务错误（HTTP 200 + body.code!=200）显式抛出，避免被吞成 success-null */
function unwrapOrThrow<T>(
  resp: { data: { code?: number; message?: string; data?: T } },
  logger: Logger,
  label: string,
  fallback: T,
): T {
  const body = resp.data;
  if (body == null) return fallback;
  if (body.code !== undefined && body.code !== 200) {
    logger.warn(`${label} 业务失败: code=${body.code} message=${body.message}`);
    throw new HttpException(body.message || `${label} 业务处理失败`, HttpStatus.BAD_REQUEST);
  }
  return (body.data as T) ?? fallback;
}

/**
 * 业务码透传版：order-service 全局异常处理器将 BizException(403) 映射为 HTTP 200 + body.code:403，
 * 此处保持业务码语义抛出（>=400 用原码），避免越权 403 被吞成 200 空结果。
 */
function unwrapBizOrThrow<T>(
  resp: { data: { code?: number; message?: string; data?: T } },
  logger: Logger,
  label: string,
  fallback: T,
): T {
  const body = resp.data;
  if (body == null) return fallback;
  if (body.code !== undefined && body.code !== 200) {
    const status = typeof body.code === 'number' && body.code >= 400 ? body.code : HttpStatus.BAD_REQUEST;
    logger.warn(`${label} 业务失败: code=${body.code} message=${body.message}`);
    throw new HttpException(body.message || `${label} 业务处理失败`, status);
  }
  return (body.data as T) ?? fallback;
}

@Injectable()
export class OrderService {
  private readonly logger = new Logger(OrderService.name);

  constructor(private readonly http: HttpService) {}

  /**
   * 坑 #63 修复：create 接收原始 JSON 字符串原样转发（rawBody），
   * 避免 19 位 Snowflake skuId 经 JS number 精度丢失。
   */
  async create(userId: string, rawBody: string): Promise<OrderDetail | null> {
    const url = `${SERVICES.orderService.url}${SERVICE_PATHS.orderCreate}`;
    const resp = await firstValueFrom(
      this.http.post(url, rawBody, {
        headers: { 'x-user-id': userId, 'content-type': 'application/json' },
      }).pipe(
        timeout(SERVICES.orderService.timeout),
        catchError((err) => {
          this.logger.warn(`order-service /order/create 失败: ${err.message}`);
          return Promise.resolve({ data: { data: null } });
        }),
      ),
    );
    return resp.data?.data ?? null;
  }

  async cancel(userId: string, orderId: string): Promise<void> {
    const url = `${SERVICES.orderService.url}${SERVICE_PATHS.orderCancel(orderId)}`;
    await firstValueFrom(
      this.http.post(url, {}, { headers: { 'x-user-id': userId } }).pipe(
        timeout(SERVICES.orderService.timeout),
        catchError((err) => {
          this.logger.warn(`order-service /order/${orderId}/cancel 失败: ${err.message}`);
          return Promise.resolve({ data: null });
        }),
      ),
    );
  }

  async detail(userId: string, orderId: string): Promise<OrderDetail | null> {
    const url = `${SERVICES.orderService.url}${SERVICE_PATHS.orderDetail(orderId)}`;
    const resp = await firstValueFrom(
      this.http.get(url, { headers: { 'x-user-id': userId } }).pipe(
        timeout(SERVICES.orderService.timeout),
        catchError((err) => {
          this.logger.warn(`order-service /order/${orderId} 失败: ${err.message}`);
          return Promise.resolve({ data: { data: null } });
        }),
      ),
    );
    return resp.data?.data ?? null;
  }

  /** 商家订单列表（多商家隔离：x-merchant-id 过滤，分页；status 可空=全部） */
  async merchantList(merchantId: string, status?: string, pageNum = 1, pageSize = 20): Promise<any> {
    const q = `pageNum=${pageNum}&pageSize=${pageSize}${status ? `&status=${encodeURIComponent(status)}` : ''}`;
    const url = `${SERVICES.orderService.url}${SERVICE_PATHS.orderMerchantList}?${q}`;
    const resp = await firstValueFrom(
      this.http.get(url, { headers: { 'x-merchant-id': merchantId } }).pipe(
        timeout(SERVICES.orderService.timeout),
        catchError((err) => {
          const st = err.response?.status;
          if (st && st >= 400) {
            throw new HttpException(err.response?.data?.message || 'order-service /order/merchant/list 失败', st);
          }
          this.logger.warn(`order-service /order/merchant/list 失败: ${err.message}`);
          return Promise.resolve({ data: { data: { records: [], total: 0 } } });
        }),
      ),
    );
    return unwrapBizOrThrow(resp, this.logger, 'order/merchant/list', { records: [], total: 0 });
  }

  /** 商家订单详情（多商家隔离：归属校验，非本商家订单 403） */
  async merchantDetail(merchantId: string, orderId: string): Promise<OrderDetail | null> {
    const url = `${SERVICES.orderService.url}${SERVICE_PATHS.orderMerchantDetail(orderId)}`;
    const resp = await firstValueFrom(
      this.http.get(url, { headers: { 'x-merchant-id': merchantId } }).pipe(
        timeout(SERVICES.orderService.timeout),
        catchError((err) => {
          const st = err.response?.status;
          if (st && st >= 400) {
            throw new HttpException(err.response?.data?.message || `order-service /order/merchant/${orderId} 失败`, st);
          }
          this.logger.warn(`order-service /order/merchant/${orderId} 失败: ${err.message}`);
          return Promise.resolve({ data: { data: null } });
        }),
      ),
    );
    return unwrapBizOrThrow(resp, this.logger, `order/merchant/${orderId}`, null);
  }

  async mine(userId: string): Promise<OrderDetail[]> {
    const url = `${SERVICES.orderService.url}${SERVICE_PATHS.orderMine}?userId=${encodeURIComponent(userId)}`;
    const resp = await firstValueFrom(
      this.http.get(url).pipe(
        timeout(SERVICES.orderService.timeout),
        catchError((err) => {
          if (err instanceof TimeoutError) {
            this.logger.warn('order-service /order/mine 超时');
          } else {
            this.logger.warn(`order-service /order/mine 失败: ${err.message}`);
          }
          return Promise.resolve({ data: { data: [] } });
        }),
      ),
    );
    return resp.data?.data ?? [];
  }

  /** 部分发货：坑 #63 精度修复——rawBody 字符串透传（skuId 19 位防 JS 精度丢失） */
  async shipPartial(userId: string, orderId: string, rawBody: string): Promise<Record<string, any> | null> {
    const url = `${SERVICES.orderService.url}${SERVICE_PATHS.orderShipPartial(orderId)}`;
    const resp = await firstValueFrom(
      this.http.post(url, rawBody, { headers: { 'x-user-id': userId, 'content-type': 'application/json' } }).pipe(
        timeout(SERVICES.orderService.timeout),
        catchError((err) => {
          this.logger.warn(`order-service /order/${orderId}/ship-partial 失败: ${err.message}`);
          return Promise.resolve({ data: { data: null } });
        }),
      ),
    );
    return unwrapOrThrow(resp, this.logger, 'order/ship-partial', null);
  }

  /** 订单发货明细 */
  async shipments(orderId: string): Promise<any[]> {
    const url = `${SERVICES.orderService.url}${SERVICE_PATHS.orderShipments(orderId)}`;
    const resp = await firstValueFrom(
      this.http.get(url).pipe(
        timeout(SERVICES.orderService.timeout),
        catchError((err) => {
          this.logger.warn(`order-service /order/${orderId}/shipments 失败: ${err.message}`);
          return Promise.resolve({ data: { data: [] } });
        }),
      ),
    );
    return resp.data?.data ?? [];
  }
}