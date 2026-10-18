# -*- coding: utf-8 -*-
"""最小复现：4 并发同 requestId，打印每个请求 HTTP code + body 片段"""
import json
import threading
import time
import urllib.error
import urllib.request
import uuid

URL = "http://localhost:8085/order/create"
rid = f"MIN-{uuid.uuid4().hex[:10]}"
out = {}
lock = threading.Lock()


def post(i):
    body = json.dumps({"requestId": rid, "skuItems": [{"skuId": 9000000000000000020, "count": 1}]}).encode()
    req = urllib.request.Request(URL, data=body, method="POST")
    req.add_header("Content-Type", "application/json")
    req.add_header("x-user-id", "10001")
    try:
        with urllib.request.urlopen(req, timeout=30) as resp:
            payload = resp.read().decode("utf-8")
            with lock:
                out[i] = f"200 {payload[:150]}"
    except urllib.error.HTTPError as e:
        with lock:
            out[i] = f"HTTP{e.code} {e.read().decode('utf-8','ignore')[:200]}"
    except Exception as e:
        with lock:
            out[i] = f"ERR {str(e)[:150]}"


ts = [threading.Thread(target=post, args=(i,)) for i in range(4)]
t0 = time.perf_counter()
for t in ts: t.start()
for t in ts: t.join()
print(f"requestId={rid} 耗时={round((time.perf_counter()-t0)*1000,0)}ms")
for i in sorted(out):
    print(f"  req{i}: {out[i]}")
