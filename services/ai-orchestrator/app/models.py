"""
智购 AI Orchestrator — Pydantic 模型
"""

from pydantic import BaseModel, Field


# ── 请求 ──


class ChatRequest(BaseModel):
    """聊天请求"""
    query: str = Field(..., min_length=1, max_length=2048, description="用户输入文本")
    userId: str = Field(..., min_length=1, max_length=64, description="当前用户 ID")
    sessionId: str = Field("", max_length=64, description="会话 ID（留空则新建）")


# ── SSE 事件 ──


class SSETokenEvent(BaseModel):
    """流式 token 事件"""
    event: str = "token"
    content: str


class SSEToolCallEvent(BaseModel):
    """工具调用事件"""
    event: str = "tool_call"
    tool: str
    args: dict


class SSEToolResultEvent(BaseModel):
    """工具结果事件"""
    event: str = "tool_result"
    tool: str
    result: str


class SSEDoneEvent(BaseModel):
    """完成事件"""
    event: str = "done"


class SSEErrorEvent(BaseModel):
    """错误事件"""
    event: str = "error"
    message: str


# ── 工具入参（供 SDK / 测试用） ──


class SearchProductsInput(BaseModel):
    keyword: str = Field(..., description="搜索关键词")
    limit: int = Field(default=10, ge=1, le=50, description="返回条数")


class GetPriceInput(BaseModel):
    skuId: str = Field(..., description="SKU ID")


class CheckInventoryInput(BaseModel):
    skuId: str = Field(..., description="SKU ID")


class GetUserProfileInput(BaseModel):
    userId: str = Field(..., description="用户 ID")


class ApplyCouponInput(BaseModel):
    userId: str = Field(..., description="用户 ID")
    items: list[str] = Field(..., description="商品 SKU ID 列表")