"""
工作流2 · 工具调用可靠性故障注入实测（超时 / 重试 / 降级）

对 ai-orchestrator 的 _http_get 统一治理（配置驱动超时 + 只读 GET 幂等重试 + 写操作不重试）
做故障注入验证：
  ① 瞬时 5xx → 重试 1 次后成功（请求次数 = 2，结果 200）
  ② 持续 5xx → 重试耗尽后抛 HTTPStatusError（请求次数 = 2）
  ③ 4xx 业务错误 → 不重试立即抛（请求次数 = 1）
  ④ 超时（慢接口 + 0.3s 超时）→ TransportError，重试后仍失败
  ⑤ retry=False → 5xx 不重试（请求次数 = 1）
  ⑥ 工具级降级：search_products 指向故障后端 → 返回「这项信息暂时没查到」，不抛异常

运行：services/ai-orchestrator/.venv/Scripts/python.exe scripts/ai-fault-injection/test_tool_reliability.py
"""

import json
import os
import sys
import threading
import time
import unittest
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path

# 必须在 import app 前设置环境变量（Settings 在模块导入时实例化）
_REPO = Path(__file__).resolve().parents[2]
_AI = _REPO / "services" / "ai-orchestrator"
sys.path.insert(0, str(_AI))
sys.path.insert(0, str(_AI / "app"))

os.environ["ZHIGOU_AI_TOOL_TIMEOUT_S"] = "0.6"
os.environ["ZHIGOU_AI_TOOL_RETRIES"] = "1"
os.environ["ZHIGOU_AI_TOOL_RETRY_DELAY_S"] = "0.05"

import asyncio  # noqa: E402
import httpx  # noqa: E402

from app.tools import _http_get, _http_post, search_products, _get_current_user  # noqa: E402
from app.config import settings  # noqa: E402

# ── 故障注入 Mock 后端 ──

_counter: dict[str, int] = {}


class FaultHandler(BaseHTTPRequestHandler):
    protocol_version = "HTTP/1.0"  # 每次请求独立连接，避免 keep-alive 下未读 body 导致 ReadError

    def _route(self):
        path = self.path.split("?")[0]
        if path == "/flaky500":
            _counter["flaky500"] = _counter.get("flaky500", 0) + 1
            if _counter["flaky500"] == 1:
                self._err(500)
            else:
                self._ok({"ok": True, "data": {"records": [{"name": "轻量运动鞋", "priceMin": 9900, "spuId": 1, "skus": [{"skuId": 2}]}]}})
        elif path == "/always500":
            _counter["always500"] = _counter.get("always500", 0) + 1
            self._err(500)
        elif path == "/notfound":
            _counter["notfound"] = _counter.get("notfound", 0) + 1
            self._err(404)
        elif path == "/slow":
            _counter["slow"] = _counter.get("slow", 0) + 1
            time.sleep(1.0)
            self._ok({"ok": True})
        elif path == "/product/page":  # search_products 的真实后端路径 → 持续 500 模拟故障
            _counter["product_page"] = _counter.get("product_page", 0) + 1
            self._err(500)
        else:
            self._ok({"ok": True})

    def _ok(self, body: dict):
        data = json.dumps(body, ensure_ascii=False).encode("utf-8")
        self.send_response(200)
        self.send_header("Content-Type", "application/json; charset=utf-8")
        self.send_header("Content-Length", str(len(data)))
        self.end_headers()
        self.wfile.write(data)

    def _err(self, code: int):
        data = json.dumps({"code": code, "msg": "injected fault"}, ensure_ascii=False).encode("utf-8")
        self.send_response(code)
        self.send_header("Content-Type", "application/json; charset=utf-8")
        self.send_header("Content-Length", str(len(data)))
        self.end_headers()
        self.wfile.write(data)

    def do_GET(self):
        self._route()

    def do_POST(self):
        length = int(self.headers.get("Content-Length", 0) or 0)
        if length:
            self.rfile.read(length)
        self._route()

    def log_message(self, *args):
        pass


class ToolReliabilityTest(unittest.IsolatedAsyncioTestCase):
    @classmethod
    def setUpClass(cls):
        cls.srv = ThreadingHTTPServer(("127.0.0.1", 0), FaultHandler)
        cls.port = cls.srv.server_address[1]
        cls.base = f"http://127.0.0.1:{cls.port}"
        cls.t = threading.Thread(target=cls.srv.serve_forever, daemon=True)
        cls.t.start()

    @classmethod
    def tearDownClass(cls):
        cls.srv.shutdown()
        cls.srv.server_close()

    async def test_01_flaky500_retry_success(self):
        """① 瞬时 5xx：重试 1 次后成功，总请求 2 次。"""
        _counter.pop("flaky500", None)
        data = await _http_get(f"{self.base}/flaky500")
        self.assertEqual(data["ok"], True)
        self.assertEqual(_counter["flaky500"], 2, "5xx 应触发 1 次重试")

    async def test_02_always500_retry_exhausted(self):
        """② 持续 5xx：重试耗尽后抛 HTTPStatusError，请求 2 次。"""
        _counter.pop("always500", None)
        with self.assertRaises(httpx.HTTPStatusError):
            await _http_get(f"{self.base}/always500")
        self.assertEqual(_counter["always500"], 2, "持续 5xx 应重试 1 次后放弃")

    async def test_03_404_no_retry(self):
        """③ 4xx 业务错误：不重试，请求 1 次。"""
        _counter.pop("notfound", None)
        with self.assertRaises(httpx.HTTPStatusError):
            await _http_get(f"{self.base}/notfound")
        self.assertEqual(_counter["notfound"], 1, "4xx 不应重试")

    async def test_04_timeout_transport_error(self):
        """④ 超时：慢接口 + 0.6s 超时 → TransportError（内部重试后仍失败）。"""
        _counter.pop("slow", None)
        with self.assertRaises(httpx.TransportError):
            await _http_get(f"{self.base}/slow", timeout=0.4)
        self.assertGreaterEqual(_counter["slow"], 2, "超时应触发重试")

    async def test_05_retry_disabled(self):
        """⑤ retry=False（写操作语义）：5xx 不重试，请求 1 次。"""
        _counter.pop("always500", None)
        with self.assertRaises(httpx.HTTPStatusError):
            await _http_get(f"{self.base}/always500", retry=False)
        self.assertEqual(_counter["always500"], 1, "禁用重试时 5xx 只请求 1 次")

    async def test_06_post_no_retry(self):
        """写操作 POST 一律不重试（防重复下单），5xx 请求 1 次即失败。"""
        _counter.pop("always500", None)
        with self.assertRaises(httpx.HTTPStatusError):
            await _http_post(f"{self.base}/always500", {"x": 1})
        self.assertEqual(_counter["always500"], 1, "POST 不应自动重试")

    async def test_07_tool_degrades_on_backend_down(self):
        """⑥ 工具级降级：search_products 指向持续 500 后端 → 返回「这项信息暂时没查到」，不抛异常。"""
        _counter.pop("product_page", None)
        token = _get_current_user.__globals__["_current_user_id"].set("10001")
        try:
            settings.product_service_url = self.base
            result = await search_products.ainvoke({"keyword": "运动鞋"})
            self.assertIn("这项信息暂时没查到", result)
            self.assertEqual(_counter.get("product_page", 0), 2, "工具内部走 _http_get，5xx 重试 1 次后降级")
        finally:
            _get_current_user.__globals__["_current_user_id"].reset(token)


if __name__ == "__main__":
    unittest.main(verbosity=2)
