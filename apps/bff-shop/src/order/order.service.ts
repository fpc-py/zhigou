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

@Injectable()
export class OrderService {
  private readonly logger = new Logger(OrderService.name);

  constructor(private readonly http: HttpService) {}

  async create(userId: string, body: CreateOrderBody): Promise<OrderDetail | null> {
    const url = `${SERVICES.orderService.url}${SERVICE_PATHS.orderCreate}`;
    const resp = await firstValueFrom(
      this.http.post(url, body, { headers: { 'x-user-id': userId } }).pipe(
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

  /** 部分发货：透传 body {skuId,count,trackingNo}，返回 {orderStatus,shippedCount,remainingCount,shipments} */
  async shipPartial(userId: string, orderId: string, body: {
    skuId: number; count: number; trackingNo?: string;
  }): Promise<Record<string, any> | null> {
    const url = `${SERVICES.orderService.url}${SERVICE_PATHS.orderShipPartial(orderId)}`;
    const resp = await firstValueFrom(
      this.http.post(url, body, { headers: { 'x-user-id': userId } }).pipe(
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
