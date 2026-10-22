# -*- coding: utf-8 -*-
"""M3 生产化路径：BFF 横向扩展 vs 下游容量（干净数据）
设计：VU 80/120/160（下游可扛区间），读 90% / 写 10%
- mode=single: 全打 3000；mode=split: 分发 3000+3002
- 附加：直连 product-service(8083) 纯读容量 = 服务层上限
"""
import json, os, statistics, threading, time, urllib.request

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
    def __init__(self, w_id, mode, dur_s, bases):
        super().__init__()
        self.w_id, self.mode, self.dur_s = w_id, mode, dur_s
        self.bases = bases
        self.lat, self.err = [], 0

    def run(self):
        end = time.perf_counter() + self.dur_s
        i = 0
        while time.perf_counter() < end:
            i += 1
            base = self.bases[self.w_id % len(self.bases)] if self.mode == "split" else self.bases[0]
            if i % 10 == 0:
                body = json.dumps({"requestId": f"m3-{self.w_id}-{i}-{int(time.time()*1000)}",
                                   "skuItems": [{"skuId": SKU, "count": 1}]})
                st, ms = call("POST", base, body)
            else:
                st, ms = call("GET", base)
            self.lat.append(ms)
            if st not in (200, 201):
                self.err += 1


def bench(mode, vu, dur, bases):
    ws = [Worker(k, mode, dur, bases) for k in range(vu)]
    t0 = time.perf_counter()
    for w in ws: w.start()
    for w in ws: w.join()
    elapsed = time.perf_counter() - t0
    all_lat = sorted(x for w in ws for x in w.lat)
    n = len(all_lat)
    err = sum(w.err for w in ws)
    pct = lambda p: all_lat[int(p * (n - 1))] if n else 0
    return {"mode": mode, "vu": vu, "n": n, "rps": n / elapsed,
            "p50": pct(0.50), "p95": pct(0.95), "p99": pct(0.99),
            "err": err, "err_rate": err / n if n else 0}


def main():
    print("== M3 压测：BFF 横向扩展（读 90%/写 10%）==", flush=True)
    bench("single", 40, 5, ["http://localhost:3000"])  # 预热
    for vu in (80, 120, 160):
        r1 = bench("single", vu, 15, ["http://localhost:3000"])
        r2 = bench("split", vu, 15, ["http://localhost:3000", "http://localhost:3002"])
        print(f"\n== {vu}VU ==", flush=True)
        print(f"单实例 : RPS={r1['rps']:.0f} P50={r1['p50']:.0f} P95={r1['p95']:.0f} 错误率={r1['err_rate']*100:.2f}% n={r1['n']}", flush=True)
        print(f"双实例 : RPS={r2['rps']:.0f} P50={r2['p50']:.0f} P95={r2['p95']:.0f} 错误率={r2['err_rate']*100:.2f}% n={r2['n']}", flush=True)
        if r1["rps"] > 0:
            print(f"扩展比: {r2['rps']/r1['rps']:.2f}x", flush=True)

    print("\n== 服务层上限：直连 product-service(8083) 纯读 ==", flush=True)
    r3 = bench("single", 200, 15, ["http://localhost:8083"])
    print(f"直连8083 : RPS={r3['rps']:.0f} P50={r3['p50']:.0f} P95={r3['p95']:.0f} 错误率={r3['err_rate']*100:.2f}% n={r3['n']}", flush=True)
    print("\n== 完成 ==", flush=True)


if __name__ == "__main__":
    main()
