"""
智购 AI Orchestrator — 对话服务（SSE 流式处理）
"""

import asyncio
import json
import logging
from pathlib import Path
from typing import Any, AsyncGenerator

from langchain_core.messages import HumanMessage, SystemMessage
from langgraph.checkpoint.memory import MemorySaver
from langgraph.graph import START, MessagesState, StateGraph
from langgraph.prebuilt import ToolNode, tools_condition

from .config import settings
from .fallback_config import llm_timeout_ms
from .fallback_handler import timeout_fallback_stream
from .tools import TOOLS, _current_user_id

logger = logging.getLogger(__name__)

# ── 会话内存（生产环境应换 RedisSaver） ──
_memory = MemorySaver()

# ── 读取系统提示词 ──
_system_prompt: str = ""


def _load_system_prompt() -> str:
    """从 prompts/system.md 读取系统提示词。"""
    path = Path(settings.system_prompt_path)
    if not path.exists():
        logger.warning("系统提示词文件不存在: %s，使用默认提示词", path)
        return "你是智购 AI 导购助手「小智」，热情友好，用数据说话。"
    return path.read_text(encoding="utf-8").strip()


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
        _graph_app = graph.compile(checkpointer=_memory)
    return _graph_app


# ── 构建初始消息列表 ──


def _build_messages(query: str) -> list:
    global _system_prompt
    if not _system_prompt:
        _system_prompt = _load_system_prompt()
    return [
        SystemMessage(content=_system_prompt),
        HumanMessage(content=query),
    ]


# ── SSE 事件生成器 ──


async def chat_stream(
    query: str,
    user_id: str,
    session_id: str,
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
        thread_config = {"configurable": {"thread_id": session_id or "default"}}

        input_messages = _build_messages(query)
        inputs = {"messages": input_messages}

        # 收集完整回复，用于后续可能的消息持久化
        response_text = ""

        # ── LLM 超时兜底 ──
        timeout_s = llm_timeout_ms() / 1000.0

        try:
            async with asyncio.timeout(timeout_s):
                async for event in graph.astream_events(inputs, thread_config, version="v2"):
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