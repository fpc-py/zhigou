# -*- coding: utf-8 -*-
"""
工作流3 #7 熔断（Resilience4j）故障注入实测脚本 v2
链路：order(8085) --/product/page--> product(8083)
场景：
  A. 正常下单（熔断 CLOSED，真实价格）
  B. 杀 product → 连续下单触发失败率阈值 → 熔断 OPEN（事件日志证据）
  C. 熔断打开后下单：快速失败 + 订单以占位价 100 降级创建（业务不中断）
  D. 重启 product → 半开放探活 → 自动恢复 CLOSED（真实价格）
证据来源：order 日志 JSON 行（python utf-8 读取，绕过 PowerShell ANSI 解码坑）
  - "[熔断] orderFetchSku 状态变化" 事件日志
  - "拉取商品信息失败" 降级 warn
  - "订单创建" 金额（真实 vs 100 占位）
用法：python test_circuit_breaker.py <product_pid>
"""
import json
import os
import time
import urllib.request

ORDER = "http://localhost:8085"
ORDER_LOG = r"D:\aafpc\Java\demo\zhigou\zhigou\target\trace-order.log"
JAVA = r"D:\app\jdk-17.0.12_windows-x64_bin\jdk-17.0.12\bin\java.exe"
JAR = r"D:\aafpc\Java\demo\zhigou\zhigou\services\product-service\target\zhigou-product-service-0.1.0-SNAPSHOT.jar"
CWD = r"D:\aafpc\Java\demo\zhigou\zhigou"


def http_json(method, url, body=None, timeout=20):
    data = json.dumps(body).encode() if body is not None else None
    req = urllib.request.Request(url, data=data, method=method,
                                 headers={"Content-Type": "application/json", "X-User-Id": "10001"})
    t0 = time.perf_counter()
    try:
        with urllib.request.urlopen(req, timeout=timeout) as r:
            body_r = r.read().decode()
            return r.status, body_r, int((time.perf_counter() - t0) * 1000)
    except urllib.error.HTTPError as e:
        return e.code, e.read().decode(), int((time.perf_counter() - t0) * 1000)
    except Exception as e:
        return -1, str(e), int((time.perf_counter() - t0) * 1000)


def grep_log(pattern, tail=20000):
    """从 order 日志尾部截取匹配 pattern 的行（UTF-8）"""
    with open(ORDER_LOG, "r", encoding="utf-8", errors="ignore") as f:
        lines = f.readlines()[-tail:]
    out = []
    for ln in lines:
        if pattern in ln:
            try:
                m = json.loads(ln)
                out.append(m.get("message", ""))
            except Exception:
                out.append(ln.strip()[:200])
    return out


def amount_of(msg):
    """从 '订单创建: orderId=.., userId=.., amount=..' 提取金额"""
    import re
    m = re.search(r"amount=(\d+)", msg)
    return int(m.group(1)) if m else None


def order(rid):
    body = {"requestId": rid, "skuItems": [{"skuId": 9000000000000000020, "count": 1}]}
    return http_json("POST", ORDER + "/order/create", body)


def main():
    pid = int(sys_argv_pid())
    lines = []

    def mark(msg):
        print(msg, flush=True)
        lines.append(msg)

    # ---------- A. 正常 ----------
    mark("== A. 正常（product 在线，CLOSED） ==")
    st, body, ms = order(f"cb2-a-{int(time.time())}")
    mark(f"A1 下单 status={st} 耗时={ms}ms")
    o = json.loads(body) if st == 200 else None
    mark(f"A2 响应 data 金额字段: {o.get('data', {}).get('payAmount') if o else 'N/A'}")

    # ---------- B. 杀 product，触发熔断 ----------
    mark("== B. 杀 product → 连续失败 → 熔断 OPEN ==")
    os.system(f"taskkill /F /PID {pid} >nul 2>&1")
    time.sleep(2)
    times = []
    for i in range(12):
        st, body, ms = order(f"cb2-b-{i}-{int(time.time())}")
        times.append(ms)
        time.sleep(0.3)
    mark(f"B 12 连发耗时: {times}")
    open_events = grep_log("[熔断] orderFetchSku 状态变化: CLOSED → OPEN")
    mark(f"B 熔断事件日志（CLOSED→OPEN）条数: {len(open_events)}")
    for e in open_events[-2:]:
        mark(f"B 事件: {e}")

    # ---------- C. 熔断打开后：快速失败 + 降级 ----------
    mark("== C. 熔断 OPEN 期间下单（快速失败 + 占位降级） ==")
    st, body, ms = order(f"cb2-c-{int(time.time())}")
    mark(f"C1 下单 status={st} 耗时={ms}ms（熔断打开，应快速失败不等待网络）")
    # 失败日志应为 CallNotPermitted（熔断拦截）而非连接错误
    mark(f"C2 失败日志: {grep_log('拉取商品信息失败')[-2:]}")
    creates = grep_log("订单创建")
    amounts = [amount_of(m) for m in creates]
    mark(f"C3 最近订单金额序列（含占位 100）: {amounts[-14:]}")

    # ---------- D. 重启 product，半开放探活恢复 ----------
    mark("== D. 重启 product → 自动恢复 ==")
    os.system(f'start "" /b "{JAVA}" -jar "{JAR}" --spring.profiles.active=dev >nul 2>&1')
    time.sleep(20)
    st, body, ms = order(f"cb2-d-{int(time.time())}")
    mark(f"D1 下单 status={st} 耗时={ms}ms")
    closed_events = grep_log("[熔断] orderFetchSku 状态变化: OPEN → HALF_OPEN") + \
                    grep_log("[熔断] orderFetchSku 状态变化: HALF_OPEN → CLOSED")
    mark(f"D2 恢复事件条数: {len(closed_events)}")
    for e in closed_events[-2:]:
        mark(f"D2 事件: {e}")
    o = json.loads(body) if st == 200 else None
    mark(f"D3 恢复后下单金额: {o.get('data', {}).get('payAmount') if o else 'N/A'}（应为真实价非 100）")

    mark("\n== 结论 ==")
    with open(os.path.join(CWD, "target", "cb2-run.txt"), "w", encoding="utf-8") as f:
        f.write("\n".join(lines))


def sys_argv_pid():
    import sys
    if len(sys.argv) > 1:
        return sys.argv[1]
    raise SystemExit("usage: python test_circuit_breaker.py <product_pid>")


if __name__ == "__main__":
    main()
