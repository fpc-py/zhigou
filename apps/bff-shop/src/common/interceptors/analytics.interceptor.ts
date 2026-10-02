/**
 * 埋点/日志拦截器
 *
 * 每个请求结束后异步记录：路径、userId、耗时、状态码
 * 初期只打日志，后续可接埋点服务
 */
import { Injectable, NestInterceptor, ExecutionContext, CallHandler, Logger } from '@nestjs/common';
import { Observable, tap } from 'rxjs';

@Injectable()
export class AnalyticsInterceptor implements NestInterceptor {
  private readonly logger = new Logger('Analytics');

  intercept(context: ExecutionContext, next: CallHandler): Observable<any> {
    const request = context.switchToHttp().getRequest();
    const start = Date.now();
    const { method, path } = request;
    const userId = (request as any).userId ?? '-';

    return next.handle().pipe(
      tap({
        next: () => {
          const duration = Date.now() - start;
          this.logger.log(`[${method}] ${path} userId=${userId} ${duration}ms`);
        },
        error: (err) => {
          const duration = Date.now() - start;
          this.logger.warn(`[${method}] ${path} userId=${userId} ${duration}ms ERROR: ${err.status ?? 500}`);
        },
      }),
    );
  }
}