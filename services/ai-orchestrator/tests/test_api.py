"""
测试：API 路由层
"""

import pytest
from fastapi.testclient import TestClient

from main import app


class TestChatAPI:
    """POST /api/v1/chat/sse 接口"""

    @pytest.fixture
    def client(self):
        return TestClient(app)

    def test_chat_sse_success(self, client):
        """测试正常请求返回 200 + SSE content-type"""
        resp = client.post(
            "/api/v1/chat/sse",
            json={"query": "你好", "userId": "u1001", "sessionId": "s001"},
        )
        assert resp.status_code == 200
        assert resp.headers.get("content-type", "").startswith("text/event-stream")
        # 确保 body 非空（实际内容依赖 LLM mock，但 content-type 正确）
        assert len(resp.content) > 0, "响应体不应为空"

    def test_chat_sse_empty_query(self, client):
        """测试 query 为空时 Pydantic 校验返回 422"""
        resp = client.post(
            "/api/v1/chat/sse",
            json={"query": "", "userId": "u1001", "sessionId": "s001"},
        )
        # Pydantic min_length=1 校验失败返回 422，非业务层面的 400
        assert resp.status_code == 422

    def test_chat_sse_missing_userId(self, client):
        """测试缺少 userId 时返回 422"""
        resp = client.post(
            "/api/v1/chat/sse",
            json={"query": "你好", "sessionId": "s001"},
        )
        assert resp.status_code == 422

    def test_health_check(self, client):
        """测试 /health 端点"""
        resp = client.get("/health")
        assert resp.status_code == 200
        data = resp.json()
        assert data["status"] == "UP"
        assert data["service"] == "ai-orchestrator"