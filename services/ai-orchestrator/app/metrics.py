"""
智购 AI Orchestrator — 进程内可观测指标（零外部依赖）

对齐《企业级工程化技术方案》可观测章节：AI 导购主链路关键指标先落地为
Prometheus 文本格式（/metrics），供采集器直接抓取；后续接入 Grafana 面板
仅需新增 dashboard，不改埋点代码。

指标覆盖（面试叙事：把「AI 链路不可见」变成「每个环节都可量化」）：
- 工具调用：次数 / 失败数 / 耗时桶（sum+count）→ 定位哪个 AI 工具拖慢对话
- LLM outcome：ok / timeout / auth / network / rate_limit / error / bad_request
  → 区分「外部 LLM 挂了」与「参数/鉴权问题」，降级决策有依据
- SSE 会话：活跃连接数 / 累计事件数 → 在线对话负载
- 其他：对话总数、token 估算、进程 uptime

并发安全：单进程 asyncio + 潜在多线程（uvicorn workers=1 默认），用 threading.Lock
保护全部计数器（桶边界用防御式拷贝，见 render）。
"""

from __future__ import annotations

import threading
import time
from collections import defaultdict

_START = time.time()
_lock = threading.Lock()

# ── 计数器（模块级单例，与 settings 同风格） ──
_tool_calls_total: dict[str, int] = defaultdict(int)
_tool_errors_total: dict[str, int] = defaultdict(int)
_tool_duration_sum: dict[str, float] = defaultdict(float)
_tool_duration_count: dict[str, int] = defaultdict(int)
_llm_outcome_total: dict[str, int] = defaultdict(int)
_llm_duration_sum = 0.0
_llm_calls_total = 0
_sse_active = 0
_sse_events_total = 0
_chat_total = 0
_tokens_estimated = 0

# LLM outcome 分类（与 chat_service 异常映射一一对应）
LLM_OUTCOMES = ("ok", "timeout", "auth", "network", "rate_limit", "error", "bad_request")


def record_tool(name: str, seconds: float, ok: bool) -> None:
    """记录一次工具调用：耗时 + 成功/失败。"""
    global _lock
    with _lock:
        _tool_calls_total[name] += 1
        _tool_duration_sum[name] += seconds
        _tool_duration_count[name] += 1
        if not ok:
            _tool_errors_total[name] += 1


def record_llm(outcome: str, seconds: float) -> None:
    """记录一次 LLM 调用结果（ok/timeout/auth/network/rate_limit/error/bad_request）。"""
    global _llm_duration_sum, _llm_calls_total
    if outcome not in LLM_OUTCOMES:
        outcome = "error"
    with _lock:
        _llm_outcome_total[outcome] += 1
        _llm_duration_sum += seconds
        _llm_calls_total += 1


def record_tokens(estimated: int) -> None:
    """累计 token 估算（len/4 粗估，仅供负载观察，非计费口径）。"""
    global _tokens_estimated
    if estimated <= 0:
        return
    with _lock:
        _tokens_estimated += estimated


def record_sse_open() -> None:
    global _sse_active, _sse_events_total
    with _lock:
        _sse_active += 1


def record_sse_event() -> None:
    global _sse_events_total
    with _lock:
        _sse_events_total += 1


def record_sse_close() -> None:
    global _sse_active
    with _lock:
        if _sse_active > 0:
            _sse_active -= 1


def record_chat() -> None:
    global _chat_total
    with _lock:
        _chat_total += 1


def _fmt(name: str, value, labels: dict[str, str] | None = None) -> str:
    if labels:
        ls = ",".join(f'{k}="{v}"' for k, v in labels.items())
        return f'{name}{{{ls}}} {value}'
    return f"{name} {value}"


def render() -> str:
    """渲染 Prometheus 文本格式（防御式拷贝，锁外读）。"""
    with _lock:
        tool_calls = dict(_tool_calls_total)
        tool_errors = dict(_tool_errors_total)
        tool_sum = dict(_tool_duration_sum)
        tool_count = dict(_tool_duration_count)
        llm_outcome = dict(_llm_outcome_total)
        llm_sum = _llm_duration_sum
        llm_calls = _llm_calls_total
        sse_active = _sse_active
        sse_events = _sse_events_total
        chat = _chat_total
        tokens = _tokens_estimated

    out: list[str] = []
    out.append("# HELP zhigou_ai_tool_calls_total AI 工具调用总次数（按工具）")
    out.append("# TYPE zhigou_ai_tool_calls_total counter")
    for name, v in sorted(tool_calls.items()):
        out.append(_fmt("zhigou_ai_tool_calls_total", v, {"tool": name}))
    out.append("# HELP zhigou_ai_tool_errors_total AI 工具执行失败次数（按工具）")
    out.append("# TYPE zhigou_ai_tool_errors_total counter")
    for name, v in sorted(tool_errors.items()):
        out.append(_fmt("zhigou_ai_tool_errors_total", v, {"tool": name}))
    out.append("# HELP zhigou_ai_tool_duration_seconds AI 工具执行耗时（sum/count，Prometheus histogram 语义）")
    out.append("# TYPE zhigou_ai_tool_duration_seconds summary")
    for name in sorted(tool_sum):
        out.append(_fmt("zhigou_ai_tool_duration_seconds_sum", f"{tool_sum[name]:.6f}", {"tool": name}))
        out.append(_fmt("zhigou_ai_tool_duration_seconds_count", tool_count[name], {"tool": name}))
    out.append("# HELP zhigou_ai_llm_calls_total LLM 调用总次数")
    out.append("# TYPE zhigou_ai_llm_calls_total counter")
    out.append(_fmt("zhigou_ai_llm_calls_total", llm_calls))
    out.append("# HELP zhigou_ai_llm_outcome_total LLM 调用结果分类（ok/timeout/auth/network/rate_limit/error/bad_request）")
    out.append("# TYPE zhigou_ai_llm_outcome_total counter")
    for name, v in sorted(llm_outcome.items()):
        out.append(_fmt("zhigou_ai_llm_outcome_total", v, {"outcome": name}))
    out.append("# HELP zhigou_ai_llm_duration_seconds LLM 调用耗时（sum/count）")
    out.append("# TYPE zhigou_ai_llm_duration_seconds summary")
    out.append(_fmt("zhigou_ai_llm_duration_seconds_sum", f"{llm_sum:.6f}"))
    out.append(_fmt("zhigou_ai_llm_duration_seconds_count", llm_calls))
    out.append("# HELP zhigou_ai_sse_active SSE 活跃连接数")
    out.append("# TYPE zhigou_ai_sse_active gauge")
    out.append(_fmt("zhigou_ai_sse_active", sse_active))
    out.append("# HELP zhigou_ai_sse_events_total SSE 累计产出事件数")
    out.append("# TYPE zhigou_ai_sse_events_total counter")
    out.append(_fmt("zhigou_ai_sse_events_total", sse_events))
    out.append("# HELP zhigou_ai_chat_total 对话请求总次数（含降级）")
    out.append("# TYPE zhigou_ai_chat_total counter")
    out.append(_fmt("zhigou_ai_chat_total", chat))
    out.append("# HELP zhigou_ai_tokens_estimated 输出 token 估算累计（len/4，负载观察口径）")
    out.append("# TYPE zhigou_ai_tokens_estimated counter")
    out.append(_fmt("zhigou_ai_tokens_estimated", tokens))
    out.append("# HELP zhigou_ai_uptime_seconds 进程运行秒数")
    out.append("# TYPE zhigou_ai_uptime_seconds gauge")
    out.append(_fmt("zhigou_ai_uptime_seconds", int(time.time() - _START)))
    return "\n".join(out) + "\n"
