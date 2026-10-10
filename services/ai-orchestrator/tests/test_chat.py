"""
测试：对话 SSE 流式输出 + 系统提示词加载（原生 OpenAI 工具循环实现）
"""

import json

import pytest

from app.chat_service import _load_system_prompt, chat_stream, _session_file


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
    """对话 SSE 流式输出（mock _run_agent 事件源）"""

    @pytest.mark.asyncio
    async def test_normal_chat_stream(self, monkeypatch):
        """测试正常对话输出 SSE 事件序列：至少包含 token 和 done"""

        import app.chat_service as cs

        sid = "test-normal"

        async def mock_run_agent(messages, timeout_s):
            yield {"event": "token", "data": json.dumps({"content": "你好！我是小智"})}

        monkeypatch.setattr(cs, "_run_agent", mock_run_agent)
        cs._system_prompt = cs._load_system_prompt()

        try:
            events = []
            async for event_str in chat_stream("你好", "u1001", sid):
                events.append(event_str)

            assert len(events) >= 2, "至少应有 2 个事件"

            first = events[0]
            assert first.startswith("event: token"), f"首个事件应为 token，实际是: {first}"
            payload = first.split("\ndata: ", 1)[1].strip()
            data = json.loads(payload)
            assert "content" in data and len(data["content"]) > 0

            last = events[-1]
            assert last.startswith("event: done"), f"最后事件应为 done，实际是: {last}"

            # 会话历史应被写回
            path = _session_file(sid)
            assert path.exists(), "会话历史应写入文件"
        finally:
            cs._system_prompt = ""
            path = _session_file(sid)
            if path.exists():
                path.unlink()

    @pytest.mark.asyncio
    async def test_chat_stream_tool_events(self, monkeypatch):
        """测试对话中包含工具调用时的事件序列"""

        import app.chat_service as cs

        sid = "test-tool"

        async def mock_run_agent(messages, timeout_s):
            yield {"event": "tool_call", "data": json.dumps({"tool": "search_products", "args": {"keyword": "手机", "limit": 10}})}
            yield {"event": "tool_result", "data": json.dumps({"tool": "search_products", "result": "· 智能手机X | ¥3999.00 | 有货"})}
            yield {"event": "token", "data": json.dumps({"content": "找到以下商品："})}

        monkeypatch.setattr(cs, "_run_agent", mock_run_agent)
        cs._system_prompt = "你是小智"

        try:
            events = []
            async for event_str in chat_stream("帮我找手机", "u1001", sid):
                events.append(event_str)

            tool_calls = [e for e in events if e.startswith("event: tool_call")]
            assert len(tool_calls) >= 1, "应有 tool_call 事件"

            tool_results = [e for e in events if e.startswith("event: tool_result")]
            assert len(tool_results) >= 1, "应有 tool_result 事件"

            done = [e for e in events if e.startswith("event: done")]
            assert len(done) >= 1, "应有 done 事件"
        finally:
            cs._system_prompt = ""
            path = _session_file(sid)
            if path.exists():
                path.unlink()
