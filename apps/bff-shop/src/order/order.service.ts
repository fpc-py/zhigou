/**
 * Order Service — 订单透传
 * 注：order-service 部分接口 userId 由调用方传入（body/query），BFF 从 JWT 提取注入。
 */
import { Injectable, Logger } from '@nestjs/common';
import { HttpService } from '@nestjs/axios';
import { firstValueFrom, catchError, timeout, TimeoutError } from 'rxjs';
import { SERVICES, SERVICE_PATHS } from '../config/service.config.js';
import type { CreateOrderBody, OrderDetail } from './order.types.js';

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
}
