"""
智购 AI Orchestrator — 对话服务（SSE 流式处理）

会话记忆：当前实现为「本地 JSON 文件持久化」（零依赖，单实例部署重启不丢）。
生产环境替换为 RedisSaver / Redis 会话存储（key: ai:session:{session_id}，TTL 7 天）。

实现说明（2026-10-10 迁移）：
- 原实现基于 langchain-openai + langgraph，其顶层 import tiktoken 的 Rust 扩展，
  在 Windows「应用程序控制策略」下 DLL 加载被阻止（ImportError: _tiktoken），
  导致新进程无法构建对话图。本版本改为原生 openai AsyncOpenAI 流式 + 手动工具循环，
  工具 schema 用 langchain_core.convert_to_openai_tool（不触发 tiktoken），对外 SSE 契约不变。
"""

import asyncio
import json
import logging
import re
from datetime import datetime
from pathlib import Path
from typing import Any, AsyncGenerator, Optional

from openai import APIError, APIConnectionError, AuthenticationError, RateLimitError, AsyncOpenAI

from .config import settings
from .fallback_config import llm_timeout_ms
from .fallback_handler import timeout_fallback_stream
from .tools import TOOLS, _current_user_id

logger = logging.getLogger(__name__)

# ── 会话持久化（生产环境应换 RedisSaver / Redis） ──
_SESSION_DIR = Path(__file__).resolve().parent.parent / "data" / "sessions"
_MAX_HISTORY_ROUNDS = 12  # 注入最近 N 轮，避免 context 膨胀

# ── 读取系统提示词 ──
_system_prompt: str = ""


def _load_system_prompt() -> str:
    """从 prompts/system.md 读取系统提示词。"""
    path = Path(settings.system_prompt_path)
    if not path.exists():
        logger.warning("系统提示词文件不存在: %s，使用默认提示词", path)
        return "你是智购 AI 导购助手「小智」，热情友好，用数据说话。"
    return path.read_text(encoding="utf-8").strip()


# ── 会话存储（本地文件，生产换 Redis） ──


def _session_file(session_id: str) -> Path:
    """会话文件路径（session_id 做安全清洗，防路径穿越）。"""
    safe = "".join(c for c in session_id if c.isalnum() or c in "-_.") or "default"
    return _SESSION_DIR / f"{safe}.json"


def _load_history(session_id: str) -> list[dict]:
    """读取会话历史（最近 N 轮），文件不存在返回空列表。"""
    path = _session_file(session_id)
    if not path.exists():
        return []
    try:
        data = json.loads(path.read_text(encoding="utf-8"))
        if not isinstance(data, list):
            return []
        return data[-_MAX_HISTORY_ROUNDS * 2 :]
    except (json.JSONDecodeError, OSError) as e:
        logger.warning("会话历史读取失败 session=%s: %s", session_id, e)
        return []


def _save_history(session_id: str, history: list[dict]) -> None:
    """追加写入会话历史（截断至最近 N 轮防膨胀）。"""
    try:
        _SESSION_DIR.mkdir(parents=True, exist_ok=True)
        path = _session_file(session_id)
        data = history[-_MAX_HISTORY_ROUNDS * 2 :]
        path.write_text(json.dumps(data, ensure_ascii=False, indent=2), encoding="utf-8")
    except OSError as e:
        logger.warning("会话历史写入失败 session=%s: %s", session_id, e)


def clear_session(session_id: str) -> bool:
    """清空指定会话（删除持久化文件）。返回是否实际删除。"""
    path = _session_file(session_id)
    if path.exists():
        try:
            path.unlink()
            logger.info("会话已清空 session=%s", session_id)
            return True
        except OSError as e:
            logger.warning("会话清空失败 session=%s: %s", session_id, e)
            return False
    return False


def is_empty_session(session_id: str) -> bool:
    """会话是否为空（无历史消息）。"""
    return not _load_history(session_id)


# ── 确定性工具路由（攒批/服务类场景强制触发，避免模型随机不调工具） ──

_ROUTE_RULES: list[tuple[str, "re.Pattern"]] = [
    ("gift_assistant", re.compile(r"送[^，。]*礼物|礼物|生日|纪念日|情人节|七夕|过节|贺卡|送[\u4e00-\u9fa5]{0,6}(妈妈|爸爸|朋友|闺蜜|同事|女票|女朋友|男朋友|家人|长辈)")),
    ("aftersale_assistant", re.compile(r"售后|退货|换货|退款|坏了|破损|质量问题|申请售后|维修|补发|质量有问题")),
    ("logistics_tracker", re.compile(r"物流|快递|运单|发货|配送|包裹|签收|到货|到哪了|送到")),
    ("usage_cycle_assistant", re.compile(r"补货|囤货|保质期|快用完|用完|换新|复购|该买|提醒我|还剩多少")),
    ("groupbuy_finder", re.compile(r"拼团|拼单|开团|参团|成团|团购")),
    ("sales_forecast", re.compile(r"我是商家|我是店主|我是运营|商家|店主|销量预测|智能选品|备货|选品建议|该备多少货|预测.{0,6}(销量|卖得好|卖得)|进货建议|经营大脑")),
    ("dynamic_pricing", re.compile(r"定价|涨价|降价|调价|价格建议|改价")),
    ("marketing_plan", re.compile(r"营销方案|做活动|促销建议|怎么推|活动策划|推广方案")),
    ("review_assistant", re.compile(r"差评|待回复评论|评论.{0,4}(怎么回|处理|回复)|负面预警|回复评论")),
]


def _route_tool(query: str) -> Optional[str]:
    """对 query 做确定性场景路由；命中返回强制工具名，未命中返回 None（模型 auto 决策）。"""
    for name, pat in _ROUTE_RULES:
        if pat.search(query):
            return name
    return None


# ── 防编造护栏 ──
# 工具返回空/未命中时，LLM 收到空结果可能自行编造商品名/品牌/价格/评分。
# 在此类结果上强制附加明确禁令，作为工具回执的一部分（LLM 对工具回执的服从度最高）。
_FORGE_GUARD = "【重要】以上结果为最终真实数据。若结果为空/暂无匹配/未查到，请直接如实告知用户「平台暂时没有查到相关商品/信息」，并可给不指名具体商品的一般性建议；严禁编造任何商品名、品牌、价格、评分、评价内容或购买链接。"


def _guard_result(result_str: str) -> str:
    """结果为空或含“暂无/未查到”语义时追加防编造护栏。"""
    s = str(result_str or "").strip()
    if not s:
        return "（工具未返回内容）" + _FORGE_GUARD
    if any(k in s for k in ("暂无", "没有搜到", "没有找到", "未查到", "没有匹配", "暂时没有", "无合适", "搜索为空")):
        return s + _FORGE_GUARD
    return s


# ── 工具 schema 与分发（原生 OpenAI 工具循环，不依赖 tiktoken） ──

_tool_schemas_cache: Optional[list[dict]] = None


def _tool_schemas() -> list[dict]:
    """从 TOOLS 函数签名/docstring 生成 OpenAI tools 参数。

    复用 langchain_core.utils.function_calling.convert_to_openai_tool
    （纯 Python 解析，不触发 tiktoken Rust 扩展）。
    """
    global _tool_schemas_cache
    if _tool_schemas_cache is None:
        from langchain_core.utils.function_calling import convert_to_openai_tool

        _tool_schemas_cache = [convert_to_openai_tool(t) for t in TOOLS]
    return _tool_schemas_cache


async def _dispatch_tool(name: str, args: dict) -> Any:
    """按工具名分发执行（TOOLS 均为 StructuredTool，用 .name 匹配）。"""
    for tool in TOOLS:
        if getattr(tool, 'name', None) == name:
            return await tool.ainvoke(args)
    raise ValueError(f"未知工具: {name}")


async def _run_agent(messages: list[dict], timeout_s: float, forced_tool: Optional[str] = None) -> AsyncGenerator[dict, None]:
    """原生 OpenAI 流式工具循环（替代 LangGraph）。

    流式产出事件（与 LangGraph astream_events 契约一致）：
      {"event": "token", "data": {"content": ...}}
      {"event": "tool_call", "data": {"tool": ..., "args": {...}}}
      {"event": "tool_result", "data": {"tool": ..., "result": ...}}
    无工具调用时结束（由 chat_stream 收尾发 done）。
    """
    client = AsyncOpenAI(base_url=settings.llm_base_url, api_key=settings.llm_api_key)

    first_round = True
    while True:
        # 仅第一轮支持确定性路由（tool_choice 强制）；后续轮走 auto 把工具结果转自然语言
        tool_choice: Any = (
            {"type": "function", "function": {"name": forced_tool}}
            if first_round and forced_tool
            else "auto"
        )
        first_round = False
        stream = await client.chat.completions.create(
            model=settings.llm_model,
            messages=messages,
            tools=_tool_schemas(),
            temperature=settings.llm_temperature,
            stream=True,
            tool_choice=tool_choice,
        )

        content_buf = ""
        tool_calls: dict[int, dict] = {}
        async for chunk in stream:
            if not chunk.choices:
                continue
            delta = chunk.choices[0].delta
            if delta.content:
                content_buf += delta.content
                yield {"event": "token", "data": json.dumps({"content": delta.content}, ensure_ascii=False)}
            for tc in delta.tool_calls or []:
                slot = tool_calls.setdefault(tc.index, {"id": "", "name": "", "args": ""})
                if tc.id:
                    slot["id"] = tc.id
                if tc.function and tc.function.name:
                    slot["name"] += tc.function.name
                if tc.function and tc.function.arguments:
                    slot["args"] += tc.function.arguments

        # 无工具调用 → 直接结束（本轮为最终答复）
        if not tool_calls:
            return

        # 追加 assistant 消息（含 tool_calls）→ 执行工具 → 追加 tool 结果 → 下一轮
        assistant_msg: dict[str, Any] = {"role": "assistant", "content": content_buf or None}
        calls_payload = []
        for idx in sorted(tool_calls):
            tc = tool_calls[idx]
            calls_payload.append(
                {
                    "id": tc["id"] or f"call_{idx}",
                    "type": "function",
                    "function": {"name": tc["name"], "arguments": tc["args"] or "{}"},
                }
            )
        assistant_msg["tool_calls"] = calls_payload
        messages.append(assistant_msg)

        for idx in sorted(tool_calls):
            tc = tool_calls[idx]
            name = tc["name"]
            try:
                args = json.loads(tc["args"] or "{}")
            except json.JSONDecodeError:
                args = {}
            safe_args = dict(args)
            if "userId" in safe_args:
                safe_args["userId"] = str(safe_args["userId"])[:3] + "***"
            yield {"event": "tool_call", "data": json.dumps({"tool": name, "args": safe_args}, ensure_ascii=False)}
            try:
                result = await _dispatch_tool(name, args)
                result_str = _guard_result(result)
            except Exception as e:  # 工具自身异常 → 反馈给 LLM 继续
                logger.warning("工具 %s 执行失败: %s", name, e)
                result_str = f"工具执行失败: {e}" + _FORGE_GUARD
            yield {"event": "tool_result", "data": json.dumps({"tool": name, "result": result_str}, ensure_ascii=False)}
            messages.append(
                {"role": "tool", "tool_call_id": tc["id"] or f"call_{idx}", "content": result_str}
            )


# ── 构建消息列表（系统提示 + 会话历史 + 当前 query，OpenAI 原生格式） ──


def _build_messages(query: str, history: Optional[list[dict]] = None, image_url: str = "") -> list[dict]:
    global _system_prompt
    if not _system_prompt:
        _system_prompt = _load_system_prompt()

    messages: list[dict] = [{"role": "system", "content": _system_prompt}]
    for item in history or []:
        role = item.get("role")
        content = item.get("content", "")
        if not content:
            continue
        messages.append({"role": "user" if role == "human" else "assistant", "content": content})

    # 图片搜款：强制系统指令 + 多模态首条消息（文本 + 图片），必须调 search_by_image
    if image_url:
        messages.append(
            {
                "role": "system",
                "content": "用户上传了商品图片。你必须先调用 search_by_image 工具（入参 image_url 为图片地址）识别图片特征并搜索同款/类似商品，禁止直接文字回答跳过工具。",
            }
        )
        messages.append(
            {
                "role": "user",
                "content": [
                    {"type": "text", "text": query or "帮我看下这张图里的商品，找同款"},
                    {"type": "image_url", "image_url": {"url": image_url}},
                ],
            }
        )
    else:
        messages.append({"role": "user", "content": query})
    return messages


# ── SSE 事件生成器 ──


async def chat_stream(
    query: str,
    user_id: str,
    session_id: str,
    image_url: str = "",
) -> AsyncGenerator[str, None]:
    """
    对话流式处理，产出 SSE 格式事件。

    事件序列：
      event: token\ndata: {"content": "..."}\n\n
      event: tool_call\ndata: {"tool": "...", "args": {...}}\n\n
      event: tool_result\ndata: {"tool": "...", "result": "..."}\n\n
      event: done\ndata: null\n\n
    """

    # 设置当前 userId 到上下文变量（tools 依赖它做权限校验）
    token = _current_user_id.set(user_id)
    try:
        # 读取系统提示词
        global _system_prompt
        if not _system_prompt:
            _system_prompt = _load_system_prompt()

        sid = session_id or "default"

        # 注入会话历史（本地持久化），保证多轮长对话跨进程/重启延续
        history = _load_history(sid)
        input_messages = _build_messages(query, history, image_url)

        # 收集完整回复，对话结束后写回会话存储
        response_text = ""

        # ── LLM 超时兜底 ──
        timeout_s = llm_timeout_ms() / 1000.0

        try:
            async with asyncio.timeout(timeout_s):
                async for event in _run_agent(input_messages, timeout_s, forced_tool=_route_tool(query)):
                    if event["event"] == "token":
                        response_text += json.loads(event["data"])["content"]
                    yield event

        except TimeoutError:
            logger.warning("LLM 超时 (timeout=%dms)，切换为兜底推荐", llm_timeout_ms())
            # 兜底流内部已 yield done，此处无需重复
            async for event in timeout_fallback_stream(user_id):
                yield event
            return

        except (AuthenticationError, APIConnectionError, APIError, RateLimitError) as e:
            # LLM 认证失败/不可达/限流 → 降级为本地兜底推荐，不让对话整链路失败
            logger.warning("LLM 调用失败 (%s)，切换为兜底推荐: %s", type(e).__name__, e)
            async for event in timeout_fallback_stream(user_id):
                yield event
            return

        # 对话成功：把本轮 (human, ai) 写回会话存储（跨进程/重启延续）
        if response_text.strip():
            new_history = history + [
                {"role": "human", "content": query},
                {"role": "ai", "content": response_text},
            ]
            _save_history(sid, new_history)

        # 完成
        yield {"event": "done", "data": "null"}

    except PermissionError as e:
        logger.error("权限校验失败: %s", e)
        yield {"event": "error", "data": json.dumps({"message": "权限校验失败"}, ensure_ascii=False)}
        yield {"event": "done", "data": "null"}
    except Exception as e:
        logger.error("对话处理异常: %s", e, exc_info=True)
        yield {"event": "error", "data": json.dumps({"message": "对话处理异常，请稍后重试"}, ensure_ascii=False)}
        yield {"event": "done", "data": "null"}
    finally:
        try:
            _current_user_id.reset(token)
        except ValueError:
            # asyncio.timeout 取消工具调用链时，generator 被 athrow 到不同 context，
            # 此处 reset 可能报「Token was created in a different Context」，忽略即可
            logger.warning("current_user_id reset 上下文不匹配（可能由 LLM 超时取消导致）")
