"""
测试：对话 SSE 流式输出 + 系统提示词加载
"""

import json
import os
import tempfile

import pytest

from app.chat_service import _load_system_prompt, chat_stream


class TestSystemPrompt:
    """系统提示词从文件加载"""

    def test_load_from_file(self):
        """测试从 prompts/system.md 读取提示词"""
        prompt = _load_system_prompt()
        assert len(prompt) > 50, "提示词内容不应为空"
        assert "小智" in prompt, "应包含 AI 名称"

    def test_load_fallback_when_file_missing(self, monkeypatch):
        """测试文件不存在时返回默认提示词"""
        monkeypatch.setattr("app.chat_service.settings.system_prompt_path", "/nonexistent/path.md")
        prompt = _load_system_prompt()
        assert "小智" in prompt
        assert len(prompt) < 50, "默认提示词应简短"


class TestChatStream:
    """对话 SSE 流式输出"""

    @pytest.mark.asyncio
    async def test_normal_chat_stream(self, monkeypatch):
        """测试正常对话输出 SSE 事件序列：至少包含 token 和 done"""

        # ── mock ChatOpenAI ──
        from unittest.mock import AsyncMock, MagicMock

        from langchain_core.messages.ai import AIMessage, AIMessageChunk

        # 构造一个 mock 的异步图来直接产出 SSE 事件
        # 直接从 chat_stream 的层级 mock — 拦截 _load_system_prompt 和 graph
        async def mock_astream_events(*args, **kwargs):
            """模拟 LangGraph 流式事件"""
            yield {
                "event": "on_chat_model_stream",
                "data": {
                    "chunk": AIMessageChunk(content="你好！我是小智")
                },
            }

        # 构造 mock graph
        mock_graph = MagicMock()
        mock_graph.astream_events = mock_astream_events

        # 注入 mock
        import app.chat_service as cs
        original_get_graph = cs._get_graph
        monkeypatch.setattr(cs, "_get_graph", lambda: mock_graph)
        # 确保 system_prompt 已加载
        cs._system_prompt = cs._load_system_prompt()

        try:
            events = []
            async for event_str in chat_stream("你好", "u1001", "test-session"):
                events.append(event_str)

            # 验证事件序列
            assert len(events) >= 2, "至少应有 2 个事件"

            # 第一个事件应为 token
            first = events[0]
            assert first.startswith("event: token"), f"首个事件应为 token，实际是: {first}"
            payload = first.split("\ndata: ", 1)[1].strip()
            data = json.loads(payload)
            assert "content" in data
            assert len(data["content"]) > 0

            # 最后一个事件应为 done
            last = events[-1]
            assert last.startswith("event: done"), f"最后事件应为 done，实际是: {last}"

        finally:
            cs._get_graph = original_get_graph

    @pytest.mark.asyncio
    async def test_chat_stream_tool_events(self, monkeypatch):
        """测试对话中包含工具调用时的事件序列"""

        from unittest.mock import MagicMock

        async def mock_astream_events(*args, **kwargs):
            yield {
                "event": "on_tool_start",
                "name": "search_products",
                "data": {
                    "input": {"keyword": "手机", "limit": 10},
                },
            }
            yield {
                "event": "on_tool_end",
                "name": "search_products",
                "data": {
                    "output": "· 智能手机X | ¥3999.00 | 有货",
                },
            }
            yield {
                "event": "on_chat_model_stream",
                "data": {
                    "chunk": type("Chunk", (), {"content": "找到以下商品："})(),
                },
            }

        mock_graph = MagicMock()
        mock_graph.astream_events = mock_astream_events

        import app.chat_service as cs
        original = cs._get_graph
        monkeypatch.setattr(cs, "_get_graph", lambda: mock_graph)
        cs._system_prompt = "你是小智"

        try:
            events = []
            async for event_str in chat_stream("帮我找手机", "u1001", "test-tool"):
                events.append(event_str)

            # 应包含 tool_call 事件
            tool_calls = [e for e in events if e.startswith("event: tool_call")]
            assert len(tool_calls) >= 1, "应有 tool_call 事件"

            # 应包含 tool_result 事件
            tool_results = [e for e in events if e.startswith("event: tool_result")]
            assert len(tool_results) >= 1, "应有 tool_result 事件"

            # 应包含 done 事件
            done = [e for e in events if e.startswith("event: done")]
            assert len(done) >= 1, "应有 done 事件"

        finally:
            cs._get_graph = original