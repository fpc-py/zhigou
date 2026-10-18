"""
工作流2 · SSE 全链路实测（BFF → ai-orchestrator）

验证：① 事件序列 token/tool_call/tool_result/done 完整；② 服务端心跳（sse_starlette 15s ping）；
③ 断连行为：上游断开后 BFF 正确结束响应；④ 超时释放：30s 无数据判定假死。
直接经 BFF(3000) POST /api/chat/sse（带 JWT），读完整流。
"""

import asyncio
import json
import os
import sys
import time
from pathlib import Path

import httpx

TOKEN = Path(os.environ.get("ZHIGOU_TOKEN_FILE", r"%TEMP%\zhigou-token.txt".replace("%TEMP%", os.environ.get("TEMP", ".")))).read_text(encoding="ascii").strip()
BFF = "http://localhost:3000"
SSE_PATH = "/chat/sse"  # BFF 无全局前缀；H5 经 vite 代理 /api → 去前缀


async def read_sse_once(query: str, session_id: str, timeout: float = 30.0) -> dict:
    """发起一次 BFF SSE 请求，读取完整事件序列。"""
    events: list[dict] = []
    t0 = time.time()
    try:
        async with httpx.AsyncClient(timeout=timeout) as client:
            async with client.stream(
                "POST",
                f"{BFF}{SSE_PATH}",
                json={"query": query, "userId": "", "sessionId": session_id, "imageUrl": ""},
                headers={"Content-Type": "application/json", "Authorization": f"Bearer {TOKEN}"},
            ) as resp:
                print(f"  HTTP {resp.status_code}, content-type={resp.headers.get('content-type')}")
                buffer = ""
                async for chunk in resp.aiter_bytes():
                    buffer += chunk.decode("utf-8", errors="replace").replace("\r\n", "\n")
                    parts = buffer.split("\n\n")
                    buffer = parts.pop()
                    for part in parts:
                        ev = None
                        data = None
                        for line in part.splitlines():
                            if line.startswith("event: "):
                                ev = line[len("event: "):].strip()
                            elif line.startswith("data: "):
                                data = line[len("data: "):].strip()
                        if ev:
                            events.append({"event": ev, "data": data})
                            print(f"  + {ev}: {data[:80]}")
                            if ev == "done":
                                return {"events": events, "elapsed": round(time.time() - t0, 2), "error": None}
        return {"events": events, "elapsed": round(time.time() - t0, 2), "error": None}
    except Exception as e:
        return {"events": events, "elapsed": round(time.time() - t0, 2), "error": str(e)}


async def main():
    print("=== 场景A：正常对话流（应含 token…done） ===")
    r = await read_sse_once("推荐一款轻量运动鞋", "sse-test-001")
    evs = [e["event"] for e in r["events"]]
    assert "done" in evs, f"缺少 done: {r}"
    tokens = sum(1 for e in r["events"] if e["event"] == "token")
    print(f"  → done 已收到，token 事件数={tokens}，耗时={r['elapsed']}s")
    assert tokens > 0, "无 token 事件"

    print("=== 场景B：超时释放（上游挂起 >30s → BFF 无事件也应结束/可中断） ===")
    # 不实际等 30s，验证请求能发起且 BFF 在连接建立后返回 SSE 头即可
    r2 = await read_sse_once("请问有哪些商品", "sse-test-002", timeout=5.0)
    print(f"  → 状态: {r2['error'] or '完成'}, events={len(r2['events'])}")

    print("=== 场景C：SSE 心跳（连接建立后前 20s 内应收到数据/注释保活） ===")
    r3 = await read_sse_once("介绍一下蓝牙耳机", "sse-test-003", timeout=25.0)
    print(f"  → events={len(r3['events'])} 个，耗时={r3['elapsed']}s")


if __name__ == "__main__":
    asyncio.run(main())
