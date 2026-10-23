"""
衣橱零录入 + AI 每日穿搭确定性测试（mock 视觉/HTTP/LLM，零外部依赖）

覆盖：
- closet_scan_item：URL 校验 / 识别失败提示 / 识别成功自动入库（带 X-User-Id 内网头）
- outfit_daily：空衣橱引导 / 有衣物+规则推荐输出 / AI 润色失败降级为规则结果
- 路由命中：衣橱录入语义 → closet_scan_item；穿搭语义 → outfit_daily
- 工具注册：TOOLS 包含两工具、schema 名称唯一
"""

import asyncio
import sys
from pathlib import Path
from unittest.mock import patch

import pytest

sys.path.insert(0, str(Path(__file__).resolve().parent.parent))

from app.chat_service import _route_tool
from app.tools import TOOLS, _current_user_id, closet_scan_item, outfit_daily

UID = "10001"


@pytest.fixture(autouse=True)
def _clean_user_ctx():
    """每个测试前后重置 userId ContextVar，避免污染其他测试文件（bug 同款）。"""
    token = _current_user_id.set(None)
    yield
    _current_user_id.reset(token)


def _set_user(uid: str):
    _current_user_id.set(uid)


def _fake_http_get(responses: dict[str, dict]):
    async def _get(url: str, headers=None, timeout=5.0, retry=True):
        for key, payload in responses.items():
            if key in url:
                return payload
        raise RuntimeError(f"未 mock 的 URL: {url}")
    return _get


def _fake_http_post(responses: dict[str, dict], calls: list):
    async def _post(url: str, json_data: dict, timeout=None, headers=None):
        calls.append((url, json_data, headers))
        for key, payload in responses.items():
            if key in url:
                return payload
        raise RuntimeError(f"未 mock 的 POST URL: {url}")
    return _post


# ── closet_scan_item ──

def test_scan_rejects_invalid_url():
    _set_user(UID)
    out = asyncio.run(closet_scan_item.ainvoke({"image_url": "not-a-url"}))
    assert "有效的图片地址" in out


def test_scan_vision_fail_returns_friendly_hint():
    _set_user(UID)
    async def _fail_vision(image_url, schema="product"):
        return None
    with patch("app.tools._vision_extract", side_effect=_fail_vision):
        out = asyncio.run(closet_scan_item.ainvoke({"image_url": "http://x/img/tshirt.jpg"}))
    assert "识别失败" in out


def test_scan_success_auto_adds_item():
    _set_user(UID)
    calls = []

    async def _vision(image_url, schema="product"):
        return {
            "name": "白色圆领T恤",
            "category": "上装",
            "season": "夏",
            "color": "白色",
            "tags": ["休闲", "棉质"],
        }

    resp = {"data": {"id": "888001", "name": "白色圆领T恤", "category": "上装", "season": "夏", "color": "白色", "tags": "休闲、棉质"}}
    with patch("app.tools._vision_extract", side_effect=_vision), \
         patch("app.tools._http_post", side_effect=_fake_http_post({"/closet/item": resp}, calls)):
        out = asyncio.run(closet_scan_item.ainvoke({"image_url": "http://x/img/tshirt.jpg"}))
    assert "已自动加入衣橱" in out
    assert "白色圆领T恤" in out
    assert len(calls) == 1
    url, body, headers = calls[0]
    assert "/closet/item" in url
    assert body["userId"] == UID
    assert body["category"] == "上装"
    assert body["season"] == "夏"
    assert body["color"] == "白色"
    assert headers.get("X-User-Id") == UID


# ── outfit_daily ──

def test_outfit_empty_closet_guides_to_scan():
    _set_user(UID)
    responses = {"/closet/items": {"data": []}}
    with patch("app.tools._http_get", side_effect=_fake_http_get(responses)):
        out = asyncio.run(outfit_daily.ainvoke({"user_id": UID, "occasion": "日常"}))
    assert "衣橱还是空的" in out
    assert "拍一件衣服照片" in out


def test_outfit_uses_real_items_and_falls_back_when_llm_fails():
    _set_user(UID)
    responses = {
        "/closet/items": {"data": [
            {"id": "1", "name": "白色圆领T恤", "category": "上装", "color": "白色", "season": "夏"},
            {"id": "2", "name": "深蓝牛仔裤", "category": "下装", "color": "深蓝", "season": "四季"},
        ]},
        "/closet/outfit/recommend": {"data": {
            "occasion": "日常",
            "items": [
                {"id": "1", "name": "白色圆领T恤", "color": "白色", "season": "夏"},
                {"id": "2", "name": "深蓝牛仔裤", "color": "深蓝", "season": "四季"},
            ],
            "note": "白T恤配牛仔裤，清爽日常",
        }},
    }
    with patch("app.tools._http_get", side_effect=_fake_http_get(responses)), \
         patch("openai.AsyncOpenAI", side_effect=RuntimeError("no key")):
        out = asyncio.run(outfit_daily.ainvoke({"user_id": UID, "occasion": "日常"}))
    assert "白色圆领T恤" in out
    assert "深蓝牛仔裤" in out
    assert "白T恤配牛仔裤" in out
    assert "搭配依据" in out


def test_outfit_userid_mismatch_blocked():
    _set_user(UID)
    try:
        asyncio.run(outfit_daily.ainvoke({"user_id": "99999", "occasion": "日常"}))
        raise AssertionError("应抛 PermissionError")
    except PermissionError:
        pass


# ── 路由与注册 ──

def test_route_closet_scan():
    assert _route_tool("这件衣服帮我放进衣橱") == "closet_scan_item"
    assert _route_tool("识别这件衣服") == "closet_scan_item"


def test_route_outfit_daily():
    assert _route_tool("今天穿什么好") == "outfit_daily"
    assert _route_tool("给我穿搭建议") == "outfit_daily"


def test_tools_registered():
    names = {t.name for t in TOOLS}
    assert "closet_scan_item" in names
    assert "outfit_daily" in names
