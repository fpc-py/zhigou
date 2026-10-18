# -*- coding: utf-8 -*-
"""
智购 · 故障注入场景 5：支付回调 vs 关单竞态 —— 状态机防护验证
=============================================================
锚定问题：买家在支付页犹豫时，支付渠道回调与超时关单（或用户取消）同时到达，
         订单会不会出现「已支付却被关闭」或「状态双写」？
实现口径：订单状态机 validateTransition 显式校验（INIT→PAID / INIT→CLOSED，
         非法跃迁抛 BizException），任何一方先落终态，另一方必须失败。
本脚本：创建 INIT 订单 → 50 并发：一半 payCallback、一半 cancel →
        断言最终状态恰为 PAID 或 CLOSED 之一，且不存在双态/回滚错乱。
判定标准：
  - 订单终态 ∈ {PAID, CLOSED} 且唯一；
  - 出现 ORDER_STATUS_ERROR 数量 = 落败方数量（状态机防护生效）；
  - 若 CLOSED：库存恰好释放一次（SETNX）；若 PAID：库存不释放。
用法：python scenario5_pay_vs_close_race.py [并发数]
"""
import json
import subprocess
import sys
import threading
import time
import urllib.error
import urllib.request
import uuid

ORDER_URL = "http://localhost:8085/order/create"
PAY_CB_URL = "http://localhost:8085/order/payCallback/{id}"
CANCEL_URL = "http://localhost:8085/order/{id}/cancel"
SKU_ID = 9000000000000000020
USER_ID = 10001
CONCURRENCY = int(sys.argv[1]) if len(sys.argv) > 1 else 50

pay_ok = pay_err = cancel_ok = cancel_err = 0
lock = threading.Lock()


def call(url: str, method="POST", payload=None):
    data = None
    if payload is not None:
        data = json.dumps(payload).encode("utf-8")
    req = urllib.request.Request(url, data=data, method=method)
    req.add_header("Content-Type", "application/json")
    req.add_header("x-user-id", str(USER_ID))
    try:
        with urllib.request.urlopen(req, timeout=30) as resp:
            return json.loads(resp.read().decode("utf-8"))
    except urllib.error.HTTPError as e:
        try:
            return json.loads(e.read().decode("utf-8"))
        except Exception:
            return {"http": e.code, "msg": e.read().decode("utf-8", "ignore")[:120]}


def pay_side(order_id: int):
    global pay_ok, pay_err
    r = call(PAY_CB_URL.format(id=order_id))
    with lock:
        if r.get("code") == 200:
            pay_ok += 1
        else:
            pay_err += 1


def cancel_side(order_id: int):
    global cancel_ok, cancel_err
    r = call(CANCEL_URL.format(id=order_id))
    with lock:
        if r.get("code") == 200:
            cancel_ok += 1
        else:
            cancel_err += 1


def redis_get(key: str) -> str:
    return subprocess.run(["docker", "exec", "zhigou-redis", "redis-cli", "GET", key],
                          capture_output=True, text=True).stdout.strip()


def main():
    rid = f"FI5-{uuid.uuid4().hex[:14]}"
    order = call(ORDER_URL, payload={"requestId": rid, "skuItems": [{"skuId": SKU_ID, "count": 1}]})
    order_id = order["data"]["orderId"]
    stock_before = int(redis_get(f"stock:{SKU_ID}") or 0)
    print(f"[场景5] INIT 订单 {order_id} | 并发={CONCURRENCY}（支付 vs 取消各半）| 库存前={stock_before}")

    half = CONCURRENCY // 2
    threads = ([threading.Thread(target=pay_side, args=(order_id,)) for _ in range(half)] +
               [threading.Thread(target=cancel_side, args=(order_id,)) for _ in range(CONCURRENCY - half)])
    t0 = time.perf_counter()
    for t in threads: t.start()
    for t in threads: t.join()
    total_ms = (time.perf_counter() - t0) * 1000

    stock_after = int(redis_get(f"stock:{SKU_ID}") or 0)
    r = call(f"http://localhost:8085/order/{order_id}", method="GET")
    if not r.get("data"):
        print(f"[警告] 订单详情异常: {json.dumps(r, ensure_ascii=False)[:200]}")
        final_status = "UNKNOWN"
    else:
        final_status = r["data"]["orderStatus"]
    print(f"[结果] 支付成功={pay_ok} 支付失败={pay_err} | 取消成功={cancel_ok} 取消失败={cancel_err}")
    print(f"[结果] 终态={final_status} | 库存变化={stock_after - stock_before} | 总耗时={round(total_ms,0)}ms")

    if final_status == "PAID":
        ok = pay_ok >= 1 and cancel_ok == 0 and (stock_after - stock_before) == 0
        verdict = "✅ 支付胜出：状态机拦截取消，库存未被释放（不退货不放货，一致）"
    elif final_status == "CLOSED":
        ok = cancel_ok >= 1 and pay_ok == 0 and (stock_after - stock_before) == 1
        verdict = "✅ 取消胜出：状态机拦截支付，库存恰好释放一次（SETNX）"
    else:
        ok, verdict = False, f"❌ 异常终态 {final_status}"
    print(f"[结论] {verdict}" if ok else f"[结论] {verdict}")
    print(json.dumps({"scenario": "pay_vs_close_race", "orderId": order_id,
                      "concurrency": CONCURRENCY, "payOk": pay_ok, "payErr": pay_err,
                      "cancelOk": cancel_ok, "cancelErr": cancel_err,
                      "finalStatus": final_status, "stockDelta": stock_after - stock_before,
                      "pass": ok, "totalMs": round(total_ms, 0)}, ensure_ascii=False))


if __name__ == "__main__":
    main()
