# -*- coding: utf-8 -*-
"""
智购 · 故障注入场景 4：outbox 消息可靠性 —— 关单双通道只释放一次
=============================================================
锚定问题：超时/取消关单后，库存要释放，但「同步 HTTP 回滚」与「ORDER_CLOSED MQ 兜底」
         两条通道都可能触发——会不会重复加回库存，导致商家多卖？
实现口径：rollbackOrder 用 Redis SETNX（inv:rb:{orderId}，TTL 7 天）保证同一订单只释放一次。
本脚本：
  1) 创建订单 → 用户取消（真实链路：同步 rollback + 写 ORDER_CLOSED outbox）
  2) 立即记录库存 → 等待 OutboxDeliveryTask 周期投递（30s）+ MQ 消费兜底
  3) 额外对同一 orderId 连续调 5 次 /inventory/rollback，验证 SETNX 幂等
判定标准：最终库存 == 初始 +1（只释放一次，双通道不重复）。
用法：python scenario4_outbox_dual_channel.py [skuId]
"""
import json
import subprocess
import sys
import time
import urllib.request
import uuid

ORDER_URL = "http://localhost:8085/order/create"
CANCEL_URL = "http://localhost:8085/order/{id}/cancel"
ROLLBACK_URL = "http://localhost:8086/inventory/rollback"
SKU_ID = int(sys.argv[1]) if len(sys.argv) > 1 else 9000000000000000020
USER_ID = 10001


def http_json(url, payload: dict, method="POST", headers=None):
    req = urllib.request.Request(url, data=json.dumps(payload).encode("utf-8"), method=method)
    req.add_header("Content-Type", "application/json")
    for k, v in (headers or {}).items():
        req.add_header(k, v)
    with urllib.request.urlopen(req, timeout=30) as resp:
        return json.loads(resp.read().decode("utf-8"))


def redis_get(key: str) -> str:
    return subprocess.run(["docker", "exec", "zhigou-redis", "redis-cli", "GET", key],
                          capture_output=True, text=True).stdout.strip()


def main():
    key = f"stock:{SKU_ID}"
    initial = int(redis_get(key) or 0)
    print(f"[场景4] 初始库存={initial} skuId={SKU_ID}")

    # 1. 下单 + 取消（真实链路）
    rid = f"FI4-{uuid.uuid4().hex[:14]}"
    order = http_json(ORDER_URL, {"requestId": rid, "skuItems": [{"skuId": SKU_ID, "count": 1}]},
                      headers={"x-user-id": str(USER_ID)})
    order_id = order["data"]["orderId"]
    http_json(CANCEL_URL.format(id=order_id), {}, headers={"x-user-id": str(USER_ID)})
    time.sleep(1)
    after_sync = int(redis_get(key) or 0)
    print(f"[场景4] 订单 {order_id} 取消完成（同步回滚通道） | 库存={after_sync} (+{after_sync - initial})")

    # 2. 等待 outbox 投递（30s 周期）+ MQ 兜底消费
    print("[场景4] 等待 OutboxDeliveryTask 投递 + ORDER_CLOSED MQ 兜底消费（35s）...")
    time.sleep(35)
    after_mq = int(redis_get(key) or 0)
    print(f"[场景4] MQ 兜底消费后 | 库存={after_mq} (+{after_mq - initial})")

    # 3. 同一 orderId 连续 5 次直接 rollback（模拟消息重复投递/重复调用）
    items = [{"skuId": SKU_ID, "count": 1}]
    for i in range(5):
        http_json(ROLLBACK_URL, {"orderId": str(order_id), "items": items})
    time.sleep(0.5)
    after_dup = int(redis_get(key) or 0)
    print(f"[场景4] 重复 rollback x5 后 | 库存={after_dup} (+{after_dup - initial})")

    released = after_dup - initial
    ok = released == 1
    print(f"[结论] 累计释放={released}（期望 1）→ {'✅ 双通道幂等成立，SETNX 只放行一次' if ok else '❌ 重复释放，需修复!'}")
    print(json.dumps({"scenario": "outbox_dual_channel", "orderId": order_id, "skuId": SKU_ID,
                      "initial": initial, "afterSync": after_sync, "afterMq": after_mq,
                      "afterDup5x": after_dup, "releasedTotal": released, "pass": ok},
                     ensure_ascii=False))


if __name__ == "__main__":
    main()
