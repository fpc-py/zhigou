/**
 * BFF 熔断器（轻量滑动窗口实现，对齐 Java 侧 ResilientHttp 参数口径）
 *
 * 状态机：CLOSED → OPEN → HALF_OPEN → CLOSED
 * - CLOSED：固定窗口内失败率 ≥ failureThreshold 且调用数 ≥ minCalls → OPEN
 * - OPEN：拒绝一切调用（快速失败，<1ms），openMs 到期 → HALF_OPEN
 * - HALF_OPEN：放行 1 个探活请求；成功 → CLOSED（重置窗口），失败 → OPEN（重置计时）
 *
 * 坑记录：#39（编程式熔断 4 调用点 Java 侧）— BFF 透传层原只有 timeout 无快失败，
 * 下游慢会让 BFF 线程堆积；本实现把快失败下沉到 HttpService 层，零侵入所有 service。
 */
export interface CircuitBreakerOptions {
  /** 统计窗口（毫秒） */
  windowMs?: number;
  /** 窗口内失败率阈值（0~1） */
  failureThreshold?: number;
  /** 触发判断的最小调用数（防止少量请求误触发） */
  minCalls?: number;
  /** 打开状态持续时间（毫秒），到期进入半开探活 */
  openMs?: number;
}

type CircuitState = 'CLOSED' | 'OPEN' | 'HALF_OPEN';

export class CircuitOpenError extends Error {
  readonly serviceKey: string;

  constructor(serviceKey: string) {
    super(`[${serviceKey}] 熔断打开，快速失败（下游不可用）`);
    this.name = 'CircuitOpenError';
    this.serviceKey = serviceKey;
  }
}

export class CircuitBreaker {
  private state: CircuitState = 'CLOSED';
  private failures = 0;
  private calls = 0;
  private windowStart = Date.now();
  private openUntil = 0;
  /** 半开窗口内已放行的探活数（对齐 Java 侧半开 3，此处取 1 简化） */
  private halfOpenTrials = 0;
  private readonly maxHalfOpenTrials = 1;
  private readonly opts: Required<CircuitBreakerOptions>;

  constructor(opts: Required<CircuitBreakerOptions>) {
    this.opts = opts;
  }

  /** 调用前检查：false = 拒绝（熔断打开） */
  allow(): boolean {
    const now = Date.now();
    if (this.state === 'OPEN') {
      if (now >= this.openUntil) {
        this.state = 'HALF_OPEN';
        this.halfOpenTrials = 0;
      } else {
        return false;
      }
    }
    if (this.state === 'HALF_OPEN') {
      if (this.halfOpenTrials >= this.maxHalfOpenTrials) return false;
      this.halfOpenTrials++;
      return true;
    }
    // CLOSED：滚动窗口重置
    if (now - this.windowStart > this.opts.windowMs) this.resetWindow(now);
    return true;
  }

  recordSuccess(): void {
    if (this.state === 'HALF_OPEN') {
      // 半开探活成功 → 恢复 CLOSED
      this.state = 'CLOSED';
      this.resetWindow(Date.now());
    } else if (this.state === 'CLOSED') {
      this.calls++;
    }
  }

  recordFailure(): void {
    if (this.state === 'HALF_OPEN') {
      // 半开探活失败 → 重新 OPEN
      this.state = 'OPEN';
      this.openUntil = Date.now() + this.opts.openMs;
      this.halfOpenTrials = 0;
      return;
    }
    if (this.state !== 'CLOSED') return;
    this.calls++;
    this.failures++;
    if (this.calls >= this.opts.minCalls && this.failures / this.calls >= this.opts.failureThreshold) {
      this.state = 'OPEN';
      this.openUntil = Date.now() + this.opts.openMs;
      this.halfOpenTrials = 0;
    }
  }

  isOpen(): boolean {
    return this.state === 'OPEN';
  }

  getState(): CircuitState {
    return this.state;
  }

  private resetWindow(now: number): void {
    this.failures = 0;
    this.calls = 0;
    this.windowStart = now;
  }
}

const DEFAULTS: Required<CircuitBreakerOptions> = {
  windowMs: 10_000,       // 滑窗 10s（对齐 Java 侧）
  failureThreshold: 0.5,  // 失败率 50%
  minCalls: 5,            // 最少 5 次调用才触发
  openMs: 10_000,         // 打开 10s
};

/** 按服务名注册的熔断器注册表（进程内单例） */
const BREAKERS = new Map<string, CircuitBreaker>();

export function getBreaker(serviceKey: string): CircuitBreaker {
  let b = BREAKERS.get(serviceKey);
  if (!b) {
    b = new CircuitBreaker({ ...DEFAULTS });
    BREAKERS.set(serviceKey, b);
  }
  return b;
}

/** 从 URL 解析服务名（url 前缀匹配 SERVICES 表） */
export function resolveServiceKey(url: string, serviceTable: Record<string, { url: string }>): string | null {
  for (const [key, entry] of Object.entries(serviceTable)) {
    if (url.startsWith(entry.url)) return key;
  }
  return null;
}
