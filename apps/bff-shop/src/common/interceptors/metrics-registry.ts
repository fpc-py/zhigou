/**
 * BFF 业务埋点指标注册表（进程内内存聚合）
 *
 * 每个请求（AnalyticsInterceptor）record 一次；/metrics 端点快照输出。
 * 路由维度：调用次数 / 成功失败 / 错误率 / avg / p50 / p95 / max，滑动 60s 窗口。
 * 说明：单实例内存聚合，横向多实例时各实例独立（面试叙事：BFF 压测瓶颈=单进程，
 * 多实例埋点天然分片，后续可接 Prometheus 直方图统一聚合）。
 */
interface RouteBucket {
  count: number;
  errors: number;
  totalMs: number;
  samples: number[]; // 最近耗时样本（窗口内）
  windowStart: number;
  lastAt: number;
}

interface RouteMetric {
  route: string;
  count: number;
  errors: number;
  errorRate: number; // 0~1
  avgMs: number;
  p50Ms: number;
  p95Ms: number;
  maxMs: number;
  qps: number; // 窗口内每秒
  lastAt: number;
}

const WINDOW_MS = 60_000;
const MAX_SAMPLES = 5_000; // 每路由最多保留样本数（防内存无限增长）

export class MetricsRegistry {
  private buckets = new Map<string, RouteBucket>();

  record(method: string, path: string, durationMs: number, ok: boolean): void {
    const route = `${method} ${path}`;
    const now = Date.now();
    let b = this.buckets.get(route);
    if (!b) {
      b = { count: 0, errors: 0, totalMs: 0, samples: [], windowStart: now, lastAt: now };
      this.buckets.set(route, b);
    }
    if (now - b.windowStart > WINDOW_MS) {
      b.count = 0;
      b.errors = 0;
      b.totalMs = 0;
      b.samples = [];
      b.windowStart = now;
    }
    b.count++;
    if (!ok) b.errors++;
    b.totalMs += durationMs;
    b.samples.push(durationMs);
    if (b.samples.length > MAX_SAMPLES) b.samples.shift();
    b.lastAt = now;
  }

  snapshot(): RouteMetric[] {
    const now = Date.now();
    const out: RouteMetric[] = [];
    for (const [route, b] of this.buckets) {
      const count = b.count;
      if (count === 0) continue;
      const sorted = [...b.samples].sort((a, z) => a - z);
      const p50 = percentile(sorted, 0.5);
      const p95 = percentile(sorted, 0.95);
      const activeWindow = Math.max(1, now - b.windowStart);
      out.push({
        route,
        count,
        errors: b.errors,
        errorRate: count ? b.errors / count : 0,
        avgMs: count ? Math.round(b.totalMs / count) : 0,
        p50Ms: p50,
        p95Ms: p95,
        maxMs: sorted[sorted.length - 1] ?? 0,
        qps: Math.round((count / activeWindow) * 1000) / 1000,
        lastAt: b.lastAt,
      });
    }
    out.sort((a, b) => b.count - a.count);
    return out;
  }
}

function percentile(sorted: number[], q: number): number {
  if (sorted.length === 0) return 0;
  const idx = Math.min(sorted.length - 1, Math.floor(sorted.length * q));
  return sorted[idx];
}

/** 进程内单例（BFF 单实例场景） */
export const metricsRegistry = new MetricsRegistry();
