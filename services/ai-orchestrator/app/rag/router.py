"""
智购 AI Orchestrator — RAG 检索路由
"""

import logging

from fastapi import APIRouter, HTTPException

from .models import RetrieveRequest, RetrieveResponse
from .service import similarity_search
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
    """
    try:
        embedder = get_embedder()
        query_vec = await embedder.embed(req.query)
        items = await similarity_search(query_vec, req.top_k)
        return RetrieveResponse(items=items)
    except Exception as e:
        logger.error("RAG 检索失败: %s", e, exc_info=True)
        raise HTTPException(status_code=500, detail="检索服务异常，请稍后重试")