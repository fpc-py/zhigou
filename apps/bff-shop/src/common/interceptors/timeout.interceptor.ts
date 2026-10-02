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
  private readonly defaultTimeout = 500;

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