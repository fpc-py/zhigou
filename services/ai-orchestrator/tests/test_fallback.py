"""
测试：降级配置 + 降级处理
"""

import asyncio
import json
import tempfile
from pathlib import Path

import pytest
from fastapi.testclient import TestClient

from app.fallback_config import (
    _DEFAULT_CONFIG_PATH,
    _config_path,
    ai_enabled,
    init_config,
    llm_timeout_ms,
    reload_config,
)
from app.fallback_handler import DISABLED_MESSAGE, disabled_fallback, timeout_fallback_stream


class TestFallbackConfig:
    """降级配置加载"""

    def test_init_with_default_file(self, monkeypatch):
        """测试初始化时从默认路径读取配置"""
        # 确保默认配置文件存在
        assert _DEFAULT_CONFIG_PATH.exists(), f"默认配置不存在: {_DEFAULT_CONFIG_PATH}"
        init_config()
        assert ai_enabled() is True
        assert llm_timeout_ms() == 1500

    def test_init_with_custom_yaml(self, tmp_path):
        """测试从自定义 YAML 文件加载"""
        yml = tmp_path / "fallback.yml"
        yml.write_text("ai:\n  enabled: false\nllm:\n  timeout_ms: 3000\n", encoding="utf-8")
        init_config(str(yml))
        assert ai_enabled() is False
        assert llm_timeout_ms() == 3000

    def test_init_with_missing_file(self, monkeypatch):
        """测试配置文件不存在时使用默认值"""
        init_config("/nonexistent/fallback.yml")
        assert ai_enabled() is True
        assert llm_timeout_ms() == 1500

    def test_reload_config(self, tmp_path):
        """测试运行时重载配置"""
        yml = tmp_path / "fallback.yml"
        yml.write_text("ai:\n  enabled: true\nllm:\n  timeout_ms: 1500\n", encoding="utf-8")
        init_config(str(yml))
        assert ai_enabled() is True

        # 修改文件并重载
        yml.write_text("ai:\n  enabled: false\nllm:\n  timeout_ms: 2000\n", encoding="utf-8")
        reload_config()
        assert ai_enabled() is False
        assert llm_timeout_ms() == 2000


class TestDisabledFallback:
    """AI 关闭降级"""

    @pytest.mark.asyncio
    async def test_disabled_fallback_returns_fixed_message(self):
        """测试 ai.enabled=false 时返回固定文案"""
        events = []
        async for event in disabled_fallback():
            events.append(event)

        assert len(events) >= 2, "至少应有 2 个事件"

        first = events[0]
        assert first.startswith("event: token")
        payload = first.split("\ndata: ", 1)[1].strip()
        data = json.loads(payload)
        assert data["content"] == DISABLED_MESSAGE

        last = events[-1]
        assert last.startswith("event: done")

    @pytest.mark.asyncio
    async def test_disabled_fallback_no_tool_events(self):
        """测试降级时不包含工具调用事件"""
        events = []
        async for event in disabled_fallback():
            events.append(event)

        tool_calls = [e for e in events if "tool_call" in e]
        assert len(tool_calls) == 0, "降级模式不应有 tool_call 事件"


class TestTimeoutFallback:
    """LLM 超时降级"""

    @pytest.mark.asyncio
    async def test_timeout_fallback_with_mocked_products(self, monkeypatch):
        """测试超时降级且推荐接口返回数据"""
        import app.fallback_handler as fh

        async def mock_fetch(*args, **kwargs):
            return [
                {"name": "跑步鞋 Air", "priceMin": 39900, "stockStatus": "有货"},
                {"name": "运动 T 恤", "priceMin": 12900, "stockStatus": "有货"},
            ]

        monkeypatch.setattr(fh, "_fetch_recommended_products", mock_fetch)

        events = []
        async for event in timeout_fallback_stream():
            events.append(event)

        token_events = [e for e in events if e.startswith("event: token")]
        assert len(token_events) >= 1, "应有 token 事件"

        first_token = token_events[0]
        payload = first_token.split("\ndata: ", 1)[1].strip()
        data = json.loads(payload)
        assert "跑步鞋" in data["content"], "降级回复应包含商品名"
        assert "¥" in data["content"], "降级回复应包含价格"

        done_events = [e for e in events if e.startswith("event: done")]
        assert len(done_events) >= 1, "应有 done 事件"

    @pytest.mark.asyncio
    async def test_timeout_fallback_empty_products(self, monkeypatch):
        """测试超时降级且推荐接口也挂了"""
        import app.fallback_handler as fh

        async def mock_empty(*args, **kwargs):
            return []

        monkeypatch.setattr(fh, "_fetch_recommended_products", mock_empty)

        events = []
        async for event in timeout_fallback_stream():
            events.append(event)

        token_events = [e for e in events if e.startswith("event: token")]
        assert len(token_events) >= 1

        first_token = token_events[0]
        payload = first_token.split("\ndata: ", 1)[1].strip()
        data = json.loads(payload)
        assert "暂时忙不过来" in data["content"], "推荐失败时应返回简洁降级文案"


class TestAPIFallback:
    """API 层降级测试"""

    @pytest.fixture
    def client(self):
        from main import app
        return TestClient(app)

    def test_disabled_mode_returns_200(self, client, tmp_path):
        """测试 ai.enabled=false 时返回 200 + 固定文案"""
        yml = tmp_path / "fallback.yml"
        yml.write_text("ai:\n  enabled: false\nllm:\n  timeout_ms: 1500\n", encoding="utf-8")
        init_config(str(yml))

        try:
            resp = client.post(
                "/api/v1/chat/sse",
                json={"query": "你好", "userId": "u1001", "sessionId": "s001"},
            )
            assert resp.status_code == 200
            assert resp.headers.get("content-type", "").startswith("text/event-stream")

            body = resp.text
            assert DISABLED_MESSAGE in body, "响应体应包含固定降级文案"
            assert "event: done" in body, "响应应包含 done 事件"
            assert "tool_call" not in body, "降级模式不应有工具调用"
        finally:
            # 恢复配置
            init_config()

    @pytest.mark.asyncio
    async def test_llm_timeout_returns_fallback(self, monkeypatch):
        """测试 LLM 超时返回兜底卡片（mock LLM 抛 TimeoutError）"""
        from app.chat_service import chat_stream

        import app.chat_service as cs
        from app.fallback_handler import timeout_fallback_stream

        # Mock: 让 _get_graph 返回一个会超时的 graph
        class SlowMockGraph:
            async def astream_events(self, *args, **kwargs):
                await asyncio.sleep(10)  # 远超测试超时时间
                yield {"event": "on_chat_model_stream", "data": {"chunk": type("C", (), {"content": "hi"})()}}

        monkeypatch.setattr(cs, "_get_graph", lambda: SlowMockGraph())
        cs._system_prompt = "test"

        # 设置极短超时
        yml_path = Path(tempfile.mktemp(suffix=".yml"))
        yml_path.write_text("ai:\n  enabled: true\nllm:\n  timeout_ms: 50\n", encoding="utf-8")

        try:
            from app.fallback_config import init_config, llm_timeout_ms
            init_config(str(yml_path))
            assert llm_timeout_ms() == 50, f"超时应为 50ms, 实际 {llm_timeout_ms()}"

            # Mock 推荐商品
            import app.fallback_handler as fh

            async def mock_fetch(*args, **kwargs):
                return [{"name": "兜底商品", "priceMin": 10000}]

            monkeypatch.setattr(fh, "_fetch_recommended_products", mock_fetch)

            # 执行超时降级
            events = []
            async for event in chat_stream("你好", "u1001", "test-timeout"):
                events.append(event)

            # 验证：应返回 200，不是 500 — 无敌对错误事件
            assert len(events) >= 2, f"至少 2 个事件，实际 {len(events)}"

            error_events = [e for e in events if e.startswith("event: error")]
            assert len(error_events) == 0, f"不应有 error 事件: {error_events}"

            token_events = [e for e in events if e.startswith("event: token")]
            assert len(token_events) >= 1, "应有 token 事件（兜底文案）"

            last = events[-1]
            assert last.startswith("event: done"), f"最后事件应为 done，实际: {last}"

        finally:
            yml_path.unlink()
            init_config()