"""
块6 · AI 链路可观测确定性测试（零外部依赖）

覆盖：
- /metrics 渲染格式（Prometheus 文本：HELP/TYPE/counter/summary/gauge 齐全）
- 计数单调递增（工具/LLM/SSE/chat/token 各指标 record 后 +1）
- 工具失败注入 → error +1（面试叙事：故障注入有据）
- Prometheus 文本可解析（指标行名=值格式）
"""

import re
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent.parent))

from app import metrics as M


def _snapshot():
    with M._lock:
        return {
            "tool_calls": dict(M._tool_calls_total),
            "tool_errors": dict(M._tool_errors_total),
            "llm": dict(M._llm_outcome_total),
            "sse_events": M._sse_events_total,
            "chat": M._chat_total,
            "tokens": M._tokens_estimated,
            "sse_active": M._sse_active,
        }


def test_render_has_all_metric_families():
    text = M.render()
    required = [
        "# HELP zhigou_ai_tool_calls_total",
        "# TYPE zhigou_ai_tool_calls_total counter",
        "# TYPE zhigou_ai_tool_errors_total counter",
        "# TYPE zhigou_ai_tool_duration_seconds summary",
        "# TYPE zhigou_ai_llm_calls_total counter",
        "# TYPE zhigou_ai_llm_outcome_total counter",
        "# TYPE zhigou_ai_llm_duration_seconds summary",
        "# TYPE zhigou_ai_sse_active gauge",
        "# TYPE zhigou_ai_sse_events_total counter",
        "# TYPE zhigou_ai_chat_total counter",
        "# TYPE zhigou_ai_tokens_estimated counter",
        "# TYPE zhigou_ai_uptime_seconds gauge",
    ]
    for r in required:
        assert r in text, f"缺指标族: {r}"


def test_render_prometheus_parseable():
    """指标行必须匹配 name value 或 name{labels} value（可被 Prometheus 解析）。"""
    text = M.render()
    lines = text.splitlines()
    for ln in lines:
        if ln.startswith("#") or not ln.strip():
            continue
        assert re.match(r"^[a-z_]+(\{.*\})? [0-9.eE+/-]+$", ln.strip()), f"非法指标行: {ln}"


def test_tool_counter_increment():
    before = _snapshot()
    M.record_tool("search_products", 0.05, True)
    M.record_tool("search_products", 0.12, True)
    M.record_tool("customer_service", 0.03, False)  # 失败注入
    after = _snapshot()
    assert after["tool_calls"]["search_products"] == before["tool_calls"].get("search_products", 0) + 2
    assert after["tool_calls"]["customer_service"] == before["tool_calls"].get("customer_service", 0) + 1
    assert after["tool_errors"]["customer_service"] == before["tool_errors"].get("customer_service", 0) + 1
    # 渲染可见
    text = M.render()
    assert 'zhigou_ai_tool_calls_total{tool="search_products"} ' in text
    assert 'zhigou_ai_tool_errors_total{tool="customer_service"} ' in text
    assert 'zhigou_ai_tool_duration_seconds_sum{tool="search_products"}' in text
    assert 'zhigou_ai_tool_duration_seconds_count{tool="customer_service"}' in text


def test_llm_outcome_counter():
    before = _snapshot()
    M.record_llm("ok", 1.2)
    M.record_llm("timeout", 20.0)
    M.record_llm("rate_limit", 0.3)
    after = _snapshot()
    assert after["llm"]["ok"] == before["llm"].get("ok", 0) + 1
    assert after["llm"]["timeout"] == before["llm"].get("timeout", 0) + 1
    assert after["llm"]["rate_limit"] == before["llm"].get("rate_limit", 0) + 1
    text = M.render()
    assert 'zhigou_ai_llm_outcome_total{outcome="ok"} ' in text
    assert 'zhigou_ai_llm_outcome_total{outcome="timeout"} ' in text


def test_llm_invalid_outcome_falls_back():
    M.record_llm("unknown_xyz", 0.5)
    text = M.render()
    assert 'outcome="unknown_xyz"' not in text
    assert 'outcome="error"' in text


def test_sse_and_chat_counters():
    before = _snapshot()
    M.record_sse_open()
    M.record_sse_event()
    M.record_sse_event()
    M.record_sse_close()
    M.record_chat()
    M.record_tokens(400)
    after = _snapshot()
    assert after["sse_events"] == before["sse_events"] + 2
    assert after["sse_active"] == before["sse_active"]  # open+close 归零
    assert after["chat"] == before["chat"] + 1
    assert after["tokens"] == before["tokens"] + 400


def test_sse_close_no_negative():
    # 防御：close 超过 open 不产生负数
    M.record_sse_close()
    assert M._sse_active >= 0
