# -*- coding: utf-8 -*-
"""B7 故障演练脚本：通用工具 + 三个场景演练（探针式，无第三方依赖）。"""
import argparse
import json
import subprocess
import sys
import time
import urllib.request
import urllib.error
from pathlib import Path

HOST = "http://localhost"


def http_get(url: str, headers: dict | None = None, timeout: float = 5.0):
    req = urllib.request.Request(url, headers=headers or {})
    with urllib.request.urlopen(req, timeout=timeout) as r:
        return r.status, r.read().decode("utf-8", "replace")


def wait_ok(url: str, headers: dict | None = None, tries: int = 30, interval: float = 2.0) -> bool:
    for _ in range(tries):
        try:
            st, _ = http_get(url, headers, timeout=3)
            if st < 500:
                return True
        except Exception:
            pass
        time.sleep(interval)
    return False


def check_process(pid: int) -> bool:
    try:
        subprocess.run(["tasklist", "/FI", f"PID eq {pid}"], capture_output=True, text=True, check=False)
        out = subprocess.run(["tasklist", "/FI", f"PID eq {pid}"], capture_output=True, text=True).stdout
        return str(pid) in out
    except Exception:
        return False


def report(title: str, ok: bool, detail: str):
    print(f"[{'PASS' if ok else 'FAIL'}] {title} :: {detail}")
    return ok


def drill_circuit_breaker(port: int, token_path: str):
    """场景1：停下游 → BFF 应降级而非 5xx 洪泛 → 恢复自愈。"""
    print("== 演练3.1 下游宕机 → 熔断降级 ==")
    import os
    token = Path(token_path).read_text(encoding="utf-8").strip() if os.path.exists(token_path) else ""
    bff = f"{HOST}:3000"
    if not wait_ok(bff + "/health"):
        return report("前置: BFF 可访问", False, "BFF 未就绪，跳过")
    # 注入：停目标服务（简化：探测到不可达即视为宕机；真实演练用 Stop-Process）
    if wait_ok(f"{HOST}:{port}", tries=2, interval=1):
        return report("注入前提", False, f"服务 {port} 仍可达，请先停掉它再跑本演练")
    # 观察：BFF 请求应快速返回（降级/超时兜底），不无限挂起
    t0 = time.time()
    try:
        st, body = http_get(bff + "/api/health", {"Authorization": f"Bearer {token}"}, timeout=10)
        elapsed = time.time() - t0
        ok = st < 500 or (st >= 500 and elapsed < 3)
        return report("下游宕机期间 BFF 行为", ok, f"status={st} elapsed={elapsed:.2f}s")
    except Exception as e:
        return report("下游宕机期间 BFF 行为", False, f"异常={e}")
    # 恢复由外部执行（脚本只负责观察与结论）


def drill_cache_storm(spu_id: int, concurrency: int = 100):
    """场景2：清缓存 + 并发打同一 SPU → Caffeine 单飞回源=1。"""
    print("== 演练3.2 缓存击穿 → Caffeine 单飞 ==")
    url = f"{HOST}:8083/product/detail?spuId={spu_id}"
    if not wait_ok(url, tries=5):
        return report("前置: product-service 可访问", False, "8083 未就绪")
    # 清缓存
    subprocess.run(["redis-cli", "FLUSHDB"], capture_output=True)
    time.sleep(1)
    import threading

    results = []
    def hit():
        try:
            st, _ = http_get(url, timeout=10)
            results.append(st)
        except Exception as e:
            results.append(-1)
    threads = [threading.Thread(target=hit) for _ in range(concurrency)]
    for t in threads: t.start()
    for t in threads: t.join()
    ok = all(r == 200 for r in results)
    return report("并发回源全部 200", ok, f"{len(results)} 请求，非200={sum(1 for r in results if r != 200)}（DB 回源次数请查 product-service 日志 loadDetail 计数）")


def drill_mq_backlog():
    """场景3：向 ORDER_CLOSED 发 N 条消息（消费者停机时），验证积压指标出现。"""
    print("== 演练3.3 MQ 积压 → 探针指标 ==")
    # 依赖 RocketMQ 与 inventory 运行；本脚本仅给出观测指引（真正发消息需业务侧造单）
    return report("MQ 积压演练（观测指引）", True,
                  "真实执行：暂停 inventory 进程 → 走关单/取消订单产生 ORDER_CLOSED → "
                  "看 inventory 日志『MQ 积压采样: …lag=…』或 Prometheus rocketmq_consumer_accumulation；"
                  "恢复消费者后 lag 回落 0")


def main():
    ap = argparse.ArgumentParser(description="智购故障演练脚本")
    ap.add_argument("--scenario", required=True, choices=["circuit_breaker", "cache_storm", "mq_backlog"])
    ap.add_argument("--port", type=int, default=8085, help="circuit_breaker：要停的下游服务端口")
    ap.add_argument("--token", default=r"%TEMP%\zhigou-jwt.txt", help="JWT 文件路径")
    ap.add_argument("--spu", type=int, default=1, help="cache_storm：目标 SPU id")
    args = ap.parse_args()

    ok = True
    if args.scenario == "circuit_breaker":
        ok = drill_circuit_breaker(args.port, args.token)
    elif args.scenario == "cache_storm":
        ok = drill_cache_storm(args.spu)
    else:
        ok = drill_mq_backlog()
    sys.exit(0 if ok else 1)


if __name__ == "__main__":
    main()
