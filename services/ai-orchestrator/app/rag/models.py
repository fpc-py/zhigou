"""
智购 AI Orchestrator — RAG Pydantic 模型
"""

from pydantic import BaseModel, Field


class RetrieveRequest(BaseModel):
    """检索请求"""
    query: str = Field(..., min_length=1, max_length=512, description="用户查询文本")
    top_k: int = Field(default=10, ge=1, le=100, description="返回条数")


class RetrieveItem(BaseModel):
    """检索结果项"""
    spu_id: int
    text: str
    score: float = Field(..., description="cosine 相似度 0~1")


class RetrieveResponse(BaseModel):
    """检索响应"""
    items: list[RetrieveItem]