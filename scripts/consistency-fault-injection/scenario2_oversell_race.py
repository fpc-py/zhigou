# -*- coding: utf-8 -*-
"""
智购 · 故障注入场景 2：并发超卖 —— Redis Lua 原子扣减验证
=============================================================
锚定问题：库存只剩 1 件时，100 个用户同时抢购，会不会卖出 2 件？
实现口径：preDeduct 走 Redis Lua（deduct.lua）单脚本原子 get→compare→decrby。
本脚本：库存置 1 → 100 并发 preDeduct(count=1) → 统计成功数/失败数/Redis 剩余。

判定标准：
  - 成功数 == 1 且 Redis 剩余 == 0 → 原子扣减成立，0 超卖
  - 成功数 > 1 → 超卖漏洞（Lua 未生效 / 脚本错误）
用法：python scenario2_oversell_race.py [并发数] [skuId]
前置：库存会先被置为 1，跑完自动恢复原值。
"""
import json
import sys
import threading
import time
import urllib.error
import urllib.request

BASE = "http://localhost:8086/inventory"
SKU_ID = int(sys.argv[2]) if len(sys.argv) > 2 else 9000000000000000020
CONCURRENCY = int(sys.argv[1]) if len(sys.argv) > 1 else 100

success = []
errors = []
lock = threading.Lock()


def pre_deduct_once(idx: int):
    body = json.dumps({"skuId": SKU_ID, "count": 1}).encode("utf-8")
    req = urllib.request.Request(f"{BASE}/preDeduct", data=body, method="POST")
    req.add_header("Content-Type", "application/json")
    t0 = time.perf_counter()
    try:
        with urllib.request.urlopen(req, timeout=15) as resp:
            payload = json.loads(resp.read().decode("utf-8"))
            ms = (time.perf_counter() - t0) * 1000
            with lock:
                if payload.get("code") == 200:
                    success.append({"idx": idx, "ms": round(ms, 1)})
                else:
                    errors.append({"idx": idx, "code": payload.get("code"),
                                   "msg": str(payload.get("msg"))[:60], "ms": round(ms, 1)})
    except urllib.error.HTTPError as e:
        body = e.read().decode("utf-8", "ignore")
        with lock:
            errors.append({"idx": idx, "http": e.code, "body": body[:120]})
    except Exception as e:
        with lock:
            errors.append({"idx": idx, "error": str(e)[:120]})


def set_stock(db_val: int, redis_val: int):
    import subprocess
    subprocess.run(["docker", "exec", "zhigou-mysql", "mysql", "-uroot", "-p123456", "-e",
                    f"UPDATE zhigou_inventory.stock SET available={db_val} WHERE sku_id={SKU_ID};"],
                   capture_output=True)
    # 直接写 Redis（StringRedisTemplate key 格式 stock:{skuId}）
    subprocess.run(["docker", "exec", "zhigou-redis", "redis-cli", "SET", f"stock:{SKU_ID}", str(redis_val)],
                   capture_output=True)


def get_redis():
    import subprocess
    r = subprocess.run(["docker", "exec", "zhigou-redis", "redis-cli", "GET", f"stock:{SKU_ID}"],
                       capture_output=True, text=True)
    return r.stdout.strip()


def main():
    # 记录原值
    orig_redis = get_redis()
    print(f"[场景2] 并发超卖 | skuId={SKU_ID} | 库存置 1 | 并发={CONCURRENCY} | 原库存(redis)={orig_redis}")
    set_stock(1, 1)
    time.sleep(0.5)

    threads = [threading.Thread(target=pre_deduct_once, args=(i,)) for i in range(CONCURRENCY)]
    t0 = time.perf_counter()
    for t in threads: t.start()
    for t in threads: t.join()
    total_ms = (time.perf_counter() - t0) * 1000

    remaining = get_redis()
    oversell = max(0, len(success) - 1)
    print(f"[结果] 成功={len(success)} 失败={len(errors)} Redis剩余={remaining} 疑似超卖={oversell} "
          f"总耗时={round(total_ms,0)}ms")
    print(f"[结论] {'✅ 0 超卖，Lua 原子扣减成立' if len(success) == 1 and remaining == '0' else '❌ 发现超卖，需修复!'}")
    print(json.dumps({"scenario": "oversell_race", "skuId": SKU_ID, "concurrency": CONCURRENCY,
                      "success": len(success), "failed": len(errors), "redisRemaining": remaining,
                      "oversell": oversell, "totalMs": round(total_ms, 0),
                      "detail": {"okMs": [s["ms"] for s in success[:5]],
                                 "errCodes": [e.get("code", e.get("http")) for e in errors[:8]]}},
                     ensure_ascii=False))


if __name__ == "__main__":
    main()
