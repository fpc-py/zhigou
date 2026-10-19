/**
 * GuardedHttpService — 带熔断守护的 HttpService
 *
 * 零侵入接入：AppModule 用本类替换 HttpService 提供者后，所有 service 的
 * `this.http.get/post/...` 自动经过熔断检查（按下游 URL 解析服务名）：
 * - 熔断 OPEN：立即抛 CircuitOpenError（快速失败，不发起网络请求）
 * - 成功/失败：记录到对应服务名的熔断窗口
 * - 原有各 service 的 catchError 降级兜底自动承接（返回空数据），无需改 service 代码
 *
 * 坑记录：#40（压测 BFF 瓶颈）— 透传层无快失败，下游慢 → BFF 线程堆积；
 * 本实现把「快速失败 + 半开探活」下沉到 HttpService 层，全链路熔断对齐 Java 侧。
 */
import { Injectable } from '@nestjs/common';
import { HttpService } from '@nestjs/axios';
import type { AxiosRequestConfig } from 'axios';
import { Observable, throwError, defer, tap } from 'rxjs';
import { getBreaker, resolveServiceKey, CircuitOpenError } from './circuit-breaker.js';
import { SERVICES } from '../config/service.config.js';

@Injectable()
export class GuardedHttpService extends HttpService {
  private guard(url: string, call: () => Observable<any>): Observable<any> {
    const key = resolveServiceKey(String(url), SERVICES as Record<string, { url: string }>);
    if (!key) return call();
    const breaker = getBreaker(key);
    // 熔断打开 → 快速失败（不发起网络请求，<1ms）
    if (!breaker.allow()) return throwError(() => new CircuitOpenError(key));
    return defer(() => call()).pipe(
      tap({
        next: () => breaker.recordSuccess(),
        error: () => breaker.recordFailure(),
      }),
    );
  }

  get<T = any>(url: string, config?: AxiosRequestConfig): Observable<any> {
    return this.guard(url, () => super.get<T>(url, config));
  }

  post<T = any>(url: string, data?: any, config?: AxiosRequestConfig): Observable<any> {
    return this.guard(url, () => super.post<T>(url, data, config));
  }

  put<T = any>(url: string, data?: any, config?: AxiosRequestConfig): Observable<any> {
    return this.guard(url, () => super.put<T>(url, data, config));
  }

  patch<T = any>(url: string, data?: any, config?: AxiosRequestConfig): Observable<any> {
    return this.guard(url, () => super.patch<T>(url, data, config));
  }

  delete<T = any>(url: string, config?: AxiosRequestConfig): Observable<any> {
    return this.guard(url, () => super.delete<T>(url, config));
  }
}
