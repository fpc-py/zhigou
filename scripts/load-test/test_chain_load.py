# -*- coding: utf-8 -*-
"""
工作流3 #9 全链路压测 + 瓶颈定位（可解释容量）v2 —— threading 真并发
黄金路径：读 80%（GET /product/page）+ 写 20%（POST /order/create 下单）
分层对比：同一并发下测 BFF(3000) 入口 vs 直连服务，定位瓶颈在转发层还是服务层。
档位：50 / 100 / 200 VU，每档 20s。
用法：python test_chain_load.py   （BFF + product/order 运行中；token 在 %TEMP%/zhigou-jwt.txt）
"""
import json
import os
import statistics
import threading
import time
import urllib.request

BFF = "http://localhost:3000"
PRODUCT = "http://localhost:8083"
ORDER = "http://localhost:8085"
TOKEN = open(os.path.join(os.environ["TEMP"], "zhigou-jwt.txt")).read().strip()
SKU = 9000000000000000020


def call(method, url, body=None, timeout=10.0):
    data = body.encode() if body else None
    req = urllib.request.Request(url, data=data, method=method, headers={
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
        self.mode = mode
        self.dur_s = dur_s
        self.lat = []
        self.err = 0

    def run(self):
        end = time.perf_counter() + self.dur_s
        i = 0
        while time.perf_counter() < end:
            i += 1
            if i % 5 == 0:  # 20% 写
                body = json.dumps({"requestId": f"lt-{self.w_id}-{i}-{int(time.time()*1000)}",
                                   "skuItems": [{"skuId": SKU, "count": 1}]})
                if self.mode == "bff":
                    st, ms = call("POST", BFF + "/order/create", body)
                else:
                    st, ms = call("POST", ORDER + "/order/create", body)
            else:  # 80% 读
                if self.mode == "bff":
                    st, ms = call("GET", BFF + "/product/page?pageNum=1&pageSize=10")
                else:
                    st, ms = call("GET", PRODUCT + "/product/page?pageNum=1&pageSize=10")
            self.lat.append(ms)
            # 注意：BFF 写路径返回 201 Created（Nest 默认），200/201 均视为成功——早期版本把 201 误判为错误
            if st not in (200, 201):
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

    def pct(p):
        return all_lat[int(p * (n - 1))] if n else 0
    return {"mode": mode, "vu": vu, "n": n, "rps": n / elapsed,
            "p50": pct(0.50), "p95": pct(0.95), "p99": pct(0.99),
            "err": err, "err_rate": err / n if n else 0}


def main():
    print("== 预热 5s（BFF 50VU）==", flush=True)
    bench("bff", 50, 5)
    for vu in (50, 100, 200):
        r1 = bench("bff", vu, 20)
        r2 = bench("direct", vu, 20)
        print(f"\n== {vu}VU ==", flush=True)
        print(f"BFF  : RPS={r1['rps']:.0f} P50={r1['p50']:.0f}ms P95={r1['p95']:.0f}ms P99={r1['p99']:.0f}ms 错误率={r1['err_rate']*100:.2f}% n={r1['n']}", flush=True)
        print(f"直连 : RPS={r2['rps']:.0f} P50={r2['p50']:.0f}ms P95={r2['p95']:.0f}ms P99={r2['p99']:.0f}ms 错误率={r2['err_rate']*100:.2f}% n={r2['n']}", flush=True)
        if r2["rps"] > 0:
            print(f"转发层开销: RPS {r1['rps']-r2['rps']:+.0f} ({100*(r1['rps']-r2['rps'])/r2['rps']:+.0f}%)", flush=True)
    print("\n== 完成，结论见 docs/生产级加固实测报告.md #9 ==", flush=True)


if __name__ == "__main__":
    main()
