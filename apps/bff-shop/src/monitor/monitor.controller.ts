/**
 * 监控端点 — GET /metrics
 * 输出 BFF 业务埋点聚合指标（路由维度：计数/错误率/耗时分布/QPS）
 * 说明：生产环境应将本端点置于内网或加独立鉴权（当前被全局 JWT 守卫保护）
 */
import { Controller, Get } from '@nestjs/common';
import { metricsRegistry } from '../common/interceptors/metrics-registry.js';

@Controller('metrics')
export class MonitorController {
  @Get()
  metrics(): any {
    const rows = metricsRegistry.snapshot();
    return {
      service: 'bff-shop',
      updatedAt: new Date().toISOString(),
      totalRoutes: rows.length,
      totalRequests: rows.reduce((s, r) => s + r.count, 0),
      routes: rows,
    };
  }
}
