# -*- coding: utf-8 -*-
"""
智购 · 故障注入场景 3：重复支付回调 —— 幂等验证
=============================================================
锚定问题：支付渠道回调（或用户连点）重复到达时，会不会重复入账/重复通知？
实现口径：payment-service mockPay 先校验签名（sha256Hex(paymentNo+secret)），
         再查支付单状态，SUCCESS 直接幂等返回；随后联动 order-service payCallback，
         order 侧 PAID 状态同样幂等返回。
本脚本：完整走一遍 下单→建支付单→30 并发同 paymentNo 回调，统计最终状态与重复入账。

判定标准：
  - payment 表仅 1 条 SUCCESS；order 状态 PAID；并发回调全部 200（幂等返回）→ 成立
用法：python scenario3_dup_pay_callback.py [并发数]
"""
import hashlib
import json
import subprocess
import sys
import threading
import time
import urllib.request
import uuid

CONCURRENCY = int(sys.argv[1]) if len(sys.argv) > 1 else 30
SECRET = "sandbox-secret-key"
AUTH_SMS_URL = "http://localhost:8080/auth/send-sms-code"
AUTH_LOGIN_URL = "http://localhost:8080/auth/login"
ORDER_URL = "http://localhost:8085/order/create"
PAY_CREATE_URL = "http://localhost:8087/payment/create"
MOCK_PAY_URL = "http://localhost:8087/payment/sandbox/mock-pay"
SKU_ID = 9000000000000000020
USER_ID = 10001
PHONE = "13800138000"

results = []
lock = threading.Lock()


def http_json(url, payload: dict, method="POST", headers=None):
    req = urllib.request.Request(url, data=json.dumps(payload).encode("utf-8"), method=method)
    req.add_header("Content-Type", "application/json")
    for k, v in (headers or {}).items():
        req.add_header(k, v)
    with urllib.request.urlopen(req, timeout=30) as resp:
        return json.loads(resp.read().decode("utf-8"))


def get_jwt() -> str:
    """本地开发链路：发验证码 → 从 Redis 取码 → 登录换 JWT"""
    http_json(AUTH_SMS_URL, {"phone": PHONE})
    time.sleep(0.5)
    code = subprocess.run(
        ["docker", "exec", "zhigou-redis", "redis-cli", "GET", f"auth:sms:{PHONE}"],
        capture_output=True, text=True).stdout.strip()
    if not code:
        raise RuntimeError("验证码获取失败")
    resp = http_json(AUTH_LOGIN_URL, {"phone": PHONE, "code": code})
    return resp["data"]["accessToken"]


def mock_pay_once(payment_no: str, sign: str, idx: int):
    t0 = time.perf_counter()
    try:
        payload = http_json(MOCK_PAY_URL, {"paymentNo": payment_no, "sign": sign})
        ms = (time.perf_counter() - t0) * 1000
        with lock:
            results.append({"idx": idx, "code": payload.get("code"), "ms": round(ms, 1)})
    except Exception as e:
        with lock:
            results.append({"idx": idx, "error": str(e)[:120]})


def main():
    # 1. 下单（真实链路拿 orderId）
    rid = f"FI3-{uuid.uuid4().hex[:14]}"
    order = http_json(ORDER_URL, {"requestId": rid, "skuItems": [{"skuId": SKU_ID, "count": 1}]},
                      headers={"x-user-id": str(USER_ID)})
    order_id = order["data"]["orderId"]
    print(f"[场景3] 下单成功 orderId={order_id}")

    # 2. 建支付单（需 JWT）
    token = get_jwt()
    pay = http_json(PAY_CREATE_URL, {"userId": USER_ID, "orderNo": str(order_id), "amount": 9900},
                    headers={"Authorization": f"Bearer {token}"})
    payment_no = pay["data"]["paymentNo"]
    sign = hashlib.sha256((payment_no + SECRET).encode("utf-8")).hexdigest()
    print(f"[场景3] 支付单 paymentNo={payment_no} sign={sign[:16]}...")

    # 3. 并发重复回调
    threads = [threading.Thread(target=mock_pay_once, args=(payment_no, sign, i))
               for i in range(CONCURRENCY)]
    t0 = time.perf_counter()
    for t in threads: t.start()
    for t in threads: t.join()
    total_ms = (time.perf_counter() - t0) * 1000

    ok = [r for r in results if r.get("code") == 200]
    err = [r for r in results if "error" in r]
    print(f"[结果] 并发回调={CONCURRENCY} HTTP200={len(ok)} 错误={len(err)} 总耗时={round(total_ms,0)}ms")
    print(f"[提示] 请核对: SELECT order_status FROM zhigou_order.order_main WHERE order_id={order_id};"
          f" SELECT COUNT(*),status FROM zhigou_payment.payment WHERE payment_no='{payment_no}' GROUP BY status;")
    print(json.dumps({"scenario": "dup_pay_callback", "orderId": order_id, "paymentNo": payment_no,
                      "concurrency": CONCURRENCY, "http200": len(ok), "errors": len(err),
                      "totalMs": round(total_ms, 0)}, ensure_ascii=False))


if __name__ == "__main__":
    main()
