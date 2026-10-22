"""
块1 · AI 客服确定性测试（不依赖真实 LLM/后端）

覆盖：
- FAQ 知识库命中 / 未命中（防编造：未命中不硬答）
- 转人工确认门（无强信号不建单，错误不骚扰红线）
- 工单创建与持久化（ST 单号 / OPEN 状态 / 落盘）
- 确定性路由命中（转人工/运费 → customer_service；售后动作 → after_sale_manager 不误路由）
- TOOLS 全量 schema 转换（35 个，防漏注册回归——bug #29/#144 同款）
"""

import asyncio
import json
import os
import sys
from pathlib import Path

import pytest

sys.path.insert(0, str(Path(__file__).resolve().parent.parent))

from app.customer_service import _match_faq, create_ticket, format_faq_answer, format_ticket, _classify
from app import customer_service as cs
from app import tools as T
from app.chat_service import _route_tool

# 测试期间用独立数据目录（避免污染真实工单数据）
_TEST_DATA = Path(__file__).resolve().parent.parent / "data" / "test_support_tickets.json"


@pytest.fixture(autouse=True)
def isolate_ticket_file(monkeypatch):
    monkeypatch.setattr(cs, "_TICKETS_FILE", _TEST_DATA)
    if _TEST_DATA.exists():
        _TEST_DATA.unlink()
    yield
    if _TEST_DATA.exists():
        _TEST_DATA.unlink()


def test_faq_hit_shipping():
    hit = _match_faq("你们的运费怎么算？")
    assert hit is not None
    topic, lines = hit
    assert topic == "运费规则"
    assert any("满 ¥99 包邮" in ln for ln in lines)
    answer = format_faq_answer(topic, lines)
    assert "【运费规则】" in answer


def test_faq_hit_return_policy():
    hit = _match_faq("退货政策是什么样的，7天无理由吗")
    assert hit is not None
    topic, _ = hit
    assert topic == "退货政策"


def test_faq_miss_returns_none():
    # 未命中不得硬答（防编造）：交由主对话链路
    assert _match_faq("今天天气怎么样") is None
    assert _match_faq("帮我写一首诗") is None


def test_ticket_create_and_persist():
    t = create_ticket("10001", "COMPLAINT", "我要投诉快递没到")
    assert t["id"].startswith("ST")
    assert len(t["id"]) == 14
    assert t["status"] == "OPEN"
    assert t["userId"] == "10001"
    assert _TEST_DATA.exists()
    stored = json.loads(_TEST_DATA.read_text(encoding="utf-8"))
    assert any(x["id"] == t["id"] for x in stored)


def test_classify():
    assert _classify("我要投诉商家") == "COMPLAINT"
    assert _classify("发票怎么开") == "INVOICE"
    assert _classify("我想退货") == "AFTERSALE"
    assert _classify("快递卡住了") == "LOGISTICS"
    assert _classify("随便聊聊") == "GENERAL"


def test_ticket_format():
    t = create_ticket("10001", "AFTERSALE", "商品坏了要退")
    out = format_ticket(t)
    assert "客服工单已创建 ✅" in out
    assert t["id"] in out


@pytest.mark.asyncio
async def test_human_requires_confirm_signal():
    # 确认门：无强信号不建单
    before = _TEST_DATA.exists()
    from app.tools import _current_user_id

    token = _current_user_id.set("10001")
    try:
        result = await T.customer_service.ainvoke({"action": "human", "issue": "运费怎么算"})
    finally:
        _current_user_id.reset(token)
    assert "请明确说" in result
    assert "ST" not in result
    # 未建单：文件仍不存在（或未新增条目）
    if _TEST_DATA.exists():
        assert json.loads(_TEST_DATA.read_text(encoding="utf-8")) == []


@pytest.mark.asyncio
async def test_human_create_ticket_with_signal():
    from app.tools import _current_user_id

    token = _current_user_id.set("10001")
    try:
        result = await T.customer_service.ainvoke({"action": "human", "issue": "我要转人工，投诉快递一直没到"})
    finally:
        _current_user_id.reset(token)
    assert "客服工单已创建 ✅" in result
    assert "ST" in result
    stored = json.loads(_TEST_DATA.read_text(encoding="utf-8"))
    assert len(stored) == 1
    assert stored[0]["category"] == "COMPLAINT"


@pytest.mark.asyncio
async def test_faq_action_answer():
    from app.tools import _current_user_id

    token = _current_user_id.set("10001")
    try:
        result = await T.customer_service.ainvoke({"action": "faq", "issue": "运费怎么算"})
    finally:
        _current_user_id.reset(token)
    assert "【运费规则】" in result
    assert "ST" not in result


def test_route_hits_customer_service():
    assert _route_tool("帮我转人工客服") == "customer_service"
    assert _route_tool("运费怎么算") == "customer_service"
    assert _route_tool("发票怎么开，我要报销") == "customer_service"
    assert _route_tool("我要投诉") == "customer_service"


def test_route_after_sale_not_misrouted():
    # 售后动作仍走售后管家，不误路由到客服
    assert _route_tool("我要退货") == "after_sale_manager"
    assert _route_tool("帮我申请退款") == "after_sale_manager"


def test_route_human_signal_beats_after_sale():
    # 坑 #71a：用户明确要人工时，即使包含物流词，也必须优先客服（不被售后/物流正则截胡）
    assert _route_tool("我要转人工投诉快递一直没到") == "customer_service"
    assert _route_tool("找人工客服，物流卡住了") == "customer_service"


def test_tools_schema_all_convert():
    """TOOLS 35 全量 schema 转换成功 + 名称唯一（防漏注册回归：bug #29/#144 同款）。"""
    from langchain_core.utils.function_calling import convert_to_openai_tool

    names = [getattr(t, "name", "") for t in T.TOOLS]
    assert len(names) == 35, f"TOOLS 应 35 个，实际 {len(names)}"
    assert len(set(names)) == len(names), "工具名必须唯一"
    assert "customer_service" in names
    assert "reminders" in names
    for t in T.TOOLS:
        schema = convert_to_openai_tool(t)
        fn = schema.get("function") or {}
        assert fn.get("name"), f"工具 {t} schema 缺 name"
        assert schema.get("type") == "function", f"工具 {t} type 异常"
        assert fn.get("parameters", {}).get("type") == "object", f"工具 {t} parameters 异常"
