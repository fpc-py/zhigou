"""
智购 AI Orchestrator — 对话服务（SSE 流式处理）

会话记忆：当前实现为「本地 JSON 文件持久化」（零依赖，单实例部署重启不丢）。
生产环境替换为 RedisSaver / Redis 会话存储（key: ai:session:{session_id}，TTL 7 天）。
"""

import asyncio
import json
import logging
from datetime import datetime
from pathlib import Path
from typing import Any, AsyncGenerator, Optional

from langchain_core.messages import AIMessage, HumanMessage, SystemMessage
from langgraph.graph import START, MessagesState, StateGraph
from langgraph.prebuilt import ToolNode, tools_condition
from openai import APIError, APIConnectionError, AuthenticationError, RateLimitError

from .config import settings
from .fallback_config import llm_timeout_ms
from .fallback_handler import timeout_fallback_stream
from .tools import TOOLS, _current_user_id

logger = logging.getLogger(__name__)

# ── 会话持久化（生产环境应换 RedisSaver / Redis） ──
# 存储目录：services/ai-orchestrator/data/sessions/{session_id}.json
# 结构：[{"role": "human"|"ai", "content": "..."}]
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
        return data[-_MAX_HISTORY_ROUNDS * 2 :]  # 每轮 2 条（human+ai）
    except (json.JSONDecodeError, OSError) as e:
        logger.warning("会话历史读取失败 session=%s: %s", session_id, e)
        return []


def _save_history(session_id: str, history: list[dict]) -> None:
    """追加写入会话历史（截断至最近 N 轮防膨胀）。"""
    try:
        _SESSION_DIR.mkdir(parents=True, exist_ok=True)
        path = _session_file(session_id)
        data = history[-_MAX_HISTORY_ROUNDS * 2 :]
        path.write_text(
            json.dumps(data, ensure_ascii=False, indent=2),
            encoding="utf-8",
        )
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


# ── 构建 LangGraph 图 ──


def _build_graph() -> StateGraph:
    # 延迟导入，避免 tiktoken DLL 在 Windows 下模块加载时报错
    from langchain_openai import ChatOpenAI  # fmt: skip

    llm = ChatOpenAI(
        base_url=settings.llm_base_url,
        api_key=settings.llm_api_key,
        model=settings.llm_model,
        temperature=settings.llm_temperature,
        streaming=True,
    )

    tool_node = ToolNode(TOOLS)
    llm_with_tools = llm.bind_tools(TOOLS)

    def call_model(state: MessagesState) -> dict[str, Any]:
        messages = state["messages"]
        return {"messages": [llm_with_tools.invoke(messages)]}

    graph = StateGraph(MessagesState)
    graph.add_node("agent", call_model)
    graph.add_node("tools", tool_node)
    graph.add_conditional_edges("agent", tools_condition)
    graph.add_edge("tools", "agent")
    graph.add_edge(START, "agent")

    return graph


_graph_app = None


def _get_graph():
    global _graph_app
    if _graph_app is None:
        graph = _build_graph()
        # 无 checkpointer：跨轮记忆由本地会话存储（_load_history/_save_history）自管理，
        # 避免 MemorySaver 进程内存 + 持久化双重续接导致历史重复
        _graph_app = graph.compile()
    return _graph_app


# ── 构建消息列表（系统提示 + 会话历史 + 当前 query） ──


def _build_messages(query: str, history: Optional[list[dict]] = None, image_url: str = "") -> list:
    global _system_prompt
    if not _system_prompt:
        _system_prompt = _load_system_prompt()

    messages: list = [SystemMessage(content=_system_prompt)]
    for item in history or []:
        role = item.get("role")
        content = item.get("content", "")
        if not content:
            continue
        if role == "human":
            messages.append(HumanMessage(content=content))
        elif role == "ai":
            messages.append(AIMessage(content=content))

    # 图片搜款：强制系统指令 + 多模态首条消息（文本 + 图片），必须调 search_by_image
    if image_url:
        messages.append(SystemMessage(content="用户上传了商品图片。你必须先调用 search_by_image 工具（入参 image_url 为图片地址）识别图片特征并搜索同款/类似商品，禁止直接文字回答跳过工具。"))
        messages.append(HumanMessage(content=[
            {"type": "text", "text": query or "帮我看下这张图里的商品，找同款"},
            {"type": "image_url", "image_url": {"url": image_url}},
        ]))
    else:
        messages.append(HumanMessage(content=query))
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

        graph = _get_graph()
        sid = session_id or "default"

        # 注入会话历史（本地持久化），保证多轮长对话跨进程/重启延续
        history = _load_history(sid)
        input_messages = _build_messages(query, history, image_url)
        inputs = {"messages": input_messages}

        # 收集完整回复，对话结束后写回会话存储
        response_text = ""

        # ── LLM 超时兜底 ──
        timeout_s = llm_timeout_ms() / 1000.0

        try:
            async with asyncio.timeout(timeout_s):
                async for event in graph.astream_events(inputs, version="v2"):
                    kind = event.get("event", "")

                    # ── LLM token 输出 ──
                    if kind == "on_chat_model_stream":
                        chunk = event.get("data", {}).get("chunk", None)
                        if chunk is not None and hasattr(chunk, "content") and chunk.content:
                            content = chunk.content
                            response_text += content
                            yield {"event": "token", "data": json.dumps({"content": content}, ensure_ascii=False)}

                    # ── 工具调用 ──
                    elif kind == "on_chat_model_start":
                        pass

                    elif kind == "on_tool_start":
                        tool_data = event.get("data", {})
                        # LangChain astream_events v2: 工具名在事件顶层 name 字段（原取自 data.name 会得到 unknown）
                        tool_name = event.get("name", "unknown")
                        tool_input = tool_data.get("input", {})
                        safe_args = dict(tool_input)
                        if "userId" in safe_args:
                            safe_args["userId"] = safe_args["userId"][:3] + "***"
                        yield {"event": "tool_call", "data": json.dumps({"tool": tool_name, "args": safe_args}, ensure_ascii=False)}

                    elif kind == "on_tool_end":
                        tool_data = event.get("data", {})
                        tool_name = event.get("name", "unknown")
                        output = tool_data.get("output", "")
                        output_str = str(output) if output else ""
                        yield {"event": "tool_result", "data": json.dumps({"tool": tool_name, "result": output_str}, ensure_ascii=False)}

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
        _current_user_id.reset(token)
