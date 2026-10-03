/**
 * 超时拦截器
 *
 * 每个下游请求默认 500ms 超时，超出返回降级。
 * SSE 和 auth 路由豁免。
 */
import { Injectable, NestInterceptor, ExecutionContext, CallHandler } from '@nestjs/common';
import { Observable, catchError, timeout, TimeoutError } from 'rxjs';

const EXEMPT_PREFIXES = ['/chat/sse', '/auth/login', '/auth/refresh', '/health'];

@Injectable()
export class TimeoutInterceptor implements NestInterceptor {
  // 入口超时需大于单次下游超时（service.config 为 2000ms），
  // 否则下游尚未返回降级数据，整体请求已先被判定超时（返回 code 500 / data null）。
  private readonly defaultTimeout = 3_000;

  intercept(context: ExecutionContext, next: CallHandler): Observable<any> {
    const request = context.switchToHttp().getRequest();

    // SSE / auth 路径不设超时
    if (EXEMPT_PREFIXES.some((p) => request.path?.startsWith(p))) {
      return next.handle();
    }

    return next.handle().pipe(
      timeout(this.defaultTimeout),
      catchError((err) => {
        if (err instanceof TimeoutError) {
          return Promise.resolve({ code: 500, message: '服务超时', data: null });
        }
        throw err;
      }),
    );
  }
}