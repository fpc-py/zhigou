# -*- coding: utf-8 -*-
"""
智购 · 故障注入场景 1：并发同 requestId 下单 —— 幂等竞态验证
=============================================================
锚定问题：买家双击"提交订单"或网络重试时，会不会重复下单？
实现口径：order-service 幂等 = selectOne(userId+requestId) 先查后插，无数据库唯一约束。
本脚本：30 并发携同 requestId 调 /order/create，统计落库订单数。

判定标准：
  - 落单数 == 1 → 幂等成立（先查后插在并发下也安全，说明有其它机制）
  - 落单数 > 1  → 真实竞态：先查后插并发穿透，需要唯一约束/分布式锁兜底
用法：python scenario1_idempotency_race.py [并发数] [skuId]
"""
import json
import sys
import threading
import time
import urllib.request
import uuid

ORDER_URL = "http://localhost:8085/order/create"
SKU_ID = sys.argv[2] if len(sys.argv) > 2 else 9000000000000000020
CONCURRENCY = int(sys.argv[1]) if len(sys.argv) > 1 else 30
USER_ID = 10001

results = []
lock = threading.Lock()


def post_once(request_id: str, idx: int):
    body = json.dumps({
        "requestId": request_id,
        "skuItems": [{"skuId": SKU_ID, "count": 1}],
    }).encode("utf-8")
    req = urllib.request.Request(ORDER_URL, data=body, method="POST")
    req.add_header("Content-Type", "application/json")
    req.add_header("x-user-id", str(USER_ID))
    t0 = time.perf_counter()
    try:
        with urllib.request.urlopen(req, timeout=30) as resp:
            payload = json.loads(resp.read().decode("utf-8"))
            elapsed_ms = (time.perf_counter() - t0) * 1000
            order_id = (payload.get("data") or {}).get("orderId")
            with lock:
                results.append({"idx": idx, "code": payload.get("code"),
                                "orderId": order_id, "ms": round(elapsed_ms, 1)})
    except Exception as e:
        with lock:
            results.append({"idx": idx, "error": str(e)[:120]})


def main():
    request_id = f"FI-{uuid.uuid4().hex[:16]}"
    print(f"[场景1] 并发同 requestId 下单 | requestId={request_id} | 并发={CONCURRENCY} | skuId={SKU_ID}")
    threads = [threading.Thread(target=post_once, args=(request_id, i)) for i in range(CONCURRENCY)]
    t0 = time.perf_counter()
    for t in threads: t.start()
    for t in threads: t.join()
    total_ms = (time.perf_counter() - t0) * 1000

    ok = [r for r in results if r.get("code") == 200]
    err = [r for r in results if "error" in r]
    distinct_orders = {r["orderId"] for r in ok if r.get("orderId") is not None}
    dup = len(ok) - len(distinct_orders)
    print(f"[结果] HTTP200={len(ok)} 错误={len(err)} 去重订单={len(distinct_orders)} "
          f"重复返回={dup} 总耗时={round(total_ms,0)}ms")
    print(f"[提示] 请在 order_main 表中核对：SELECT COUNT(*) FROM zhigou_order.order_main "
          f"WHERE request_id='{request_id}';")
    print(json.dumps({"scenario": "idempotency_race", "requestId": request_id,
                      "concurrency": CONCURRENCY, "http200": len(ok), "errors": len(err),
                      "distinctOrderIds": len(distinct_orders), "duplicateResponses": dup,
                      "totalMs": round(total_ms, 0)}, ensure_ascii=False))


if __name__ == "__main__":
    main()
