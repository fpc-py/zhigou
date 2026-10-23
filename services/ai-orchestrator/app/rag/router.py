"""
智购 AI Orchestrator — RAG 检索路由
"""

import logging

from fastapi import APIRouter, HTTPException

from .models import RetrieveRequest, RetrieveResponse
from .service import similarity_search, keyword_search
from ..embedding import get_embedder

logger = logging.getLogger(__name__)

router = APIRouter(prefix="/api/v1/rag", tags=["rag"])


@router.post("/retrieve", response_model=RetrieveResponse)
async def retrieve(req: RetrieveRequest):
    """
    语义检索商品。

    1. 将 query 做 embedding → 向量
    2. 在 pgvector 中做 cosine similarity 搜索
    3. 返回 top_k 个 SPU

    弹性降级：embedding 端点不可用（如 DeepSeek 无 embeddings 端点）或
    向量检索无命中时，自动降级为关键词检索（products_vec.text ILIKE），
    保证接口永不 500。
    """
    try:
        embedder = get_embedder()
        query_vec = await embedder.embed(req.query)
        items = await similarity_search(query_vec, req.top_k)
        if not items:
            # 语义无命中 → 关键词兜底，避免空转
            items = await keyword_search(req.query, req.top_k)
        return RetrieveResponse(items=items)
    except Exception as e:
        logger.error("RAG 语义检索失败，降级关键词检索: %s", e, exc_info=True)
        try:
            items = await keyword_search(req.query, req.top_k)
            if items:
                return RetrieveResponse(items=items)
        except Exception as e2:
            logger.error("关键词兜底检索也失败: %s", e2, exc_info=True)
        raise HTTPException(status_code=500, detail="检索服务异常，请稍后重试")