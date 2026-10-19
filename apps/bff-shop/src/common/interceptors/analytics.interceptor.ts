/**
 * 埋点/日志拦截器
 *
 * 每个请求结束后：
 * 1. 异步记录：路径、userId、耗时、状态码（日志）
 * 2. 记录业务指标到 MetricsRegistry（路由维度计数/错误率/耗时分布，见 /metrics 端点）
 */
import { Injectable, NestInterceptor, ExecutionContext, CallHandler, Logger } from '@nestjs/common';
import { Observable, tap } from 'rxjs';
import { metricsRegistry } from './metrics-registry.js';

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
          metricsRegistry.record(method, path, duration, true);
          this.logger.log(`[${method}] ${path} userId=${userId} ${duration}ms`);
        },
        error: (err) => {
          const duration = Date.now() - start;
          metricsRegistry.record(method, path, duration, false);
          this.logger.warn(`[${method}] ${path} userId=${userId} ${duration}ms ERROR: ${err.status ?? 500}`);
        },
      }),
    );
  }
}