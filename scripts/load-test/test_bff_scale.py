# -*- coding: utf-8 -*-
"""
工作流3 #9 落地：BFF 横向扩展验证（多实例线性扩展）
基线：单实例 BFF(3000) ~170 RPS（200VU 饱和）
验证：200VU 轮询分发到 2 实例(3000/3002) —— 若合计 RPS ≈ 2×基线，证明瓶颈=BFF 单进程、横向扩展有效
用法：python test_bff_scale.py  （BFF1=3000 BFF2=3002 运行中；token 在 %TEMP%/zhigou-jwt.txt）
"""
import json
import os
import statistics
import threading
import time
import urllib.request

BFFS = ["http://localhost:3000", "http://localhost:3002"]
TOKEN = open(os.path.join(os.environ["TEMP"], "zhigou-jwt.txt")).read().strip()
SKU = 9000000000000000020


def call(method, base, body=None, timeout=10.0):
    data = body.encode() if body else None
    req = urllib.request.Request(base + ("/order/create" if body else "/product/page?pageNum=1&pageSize=10"),
                                 data=data, method=method, headers={
                                     "Content-Type": "application/json", "X-User-Id": "10001",
                                     "Authorization": f"Bearer {TOKEN}"})
    t0 = time.perf_counter()
    try:
        with urllib.request.urlopen(req, timeout=timeout) as r:
            return r.status, (time.perf_counter() - t0) * 1000
    except Exception:
        return -1, (time.perf_counter() - t0) * 1000


class Worker(threading.Thread):
    def __init__(self, w_id, mode, dur_s):
        super().__init__()
        self.w_id = w_id
        self.mode = mode      # "single" 全打 3000；"split" 按 id 奇偶分流
        self.dur_s = dur_s
        self.lat = []
        self.err = 0

    def run(self):
        end = time.perf_counter() + self.dur_s
        i = 0
        while time.perf_counter() < end:
            i += 1
            base = BFFS[self.w_id % 2] if self.mode == "split" else BFFS[0]
            if i % 5 == 0:  # 20% 写
                body = json.dumps({"requestId": f"sc-{self.w_id}-{i}-{int(time.time()*1000)}",
                                   "skuItems": [{"skuId": SKU, "count": 1}]})
                st, ms = call("POST", base, body)
            else:  # 80% 读
                st, ms = call("GET", base)
            self.lat.append(ms)
            if st not in (200, 201):  # 201 = 写成功（Nest 默认）
                self.err += 1


def bench(mode, vu, dur):
    ws = [Worker(k, mode, dur) for k in range(vu)]
    t0 = time.perf_counter()
    for w in ws:
        w.start()
    for w in ws:
        w.join()
    elapsed = time.perf_counter() - t0
    all_lat = sorted(x for w in ws for x in w.lat)
    n = len(all_lat)
    err = sum(w.err for w in ws)
    pct = lambda p: all_lat[int(p * (n - 1))] if n else 0
    return {"mode": mode, "vu": vu, "n": n, "rps": n / elapsed,
            "p50": pct(0.50), "p95": pct(0.95), "p99": pct(0.99),
            "err": err, "err_rate": err / n if n else 0}


def main():
    print("== 预热 5s（单实例 50VU）==", flush=True)
    bench("single", 50, 5)
    for vu in (100, 200, 300):
        r1 = bench("single", vu, 20)
        r2 = bench("split", vu, 20)
        print(f"\n== {vu}VU ==", flush=True)
        print(f"单实例 : RPS={r1['rps']:.0f} P50={r1['p50']:.0f}ms P95={r1['p95']:.0f}ms P99={r1['p99']:.0f}ms 错误率={r1['err_rate']*100:.2f}% n={r1['n']}", flush=True)
        print(f"双实例 : RPS={r2['rps']:.0f} P50={r2['p50']:.0f}ms P95={r2['p95']:.0f}ms P99={r2['p99']:.0f}ms 错误率={r2['err_rate']*100:.2f}% n={r2['n']}", flush=True)
        if r1["rps"] > 0:
            print(f"扩展比: {r2['rps']/r1['rps']:.2f}x", flush=True)
    print("\n== 完成，结论写入 docs/生产级加固实测报告.md #9 落地 ==", flush=True)


if __name__ == "__main__":
    main()
