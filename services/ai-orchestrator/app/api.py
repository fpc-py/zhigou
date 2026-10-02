"""
智购 AI Orchestrator — API 路由
"""

import logging

from fastapi import APIRouter, HTTPException
from sse_starlette.sse import EventSourceResponse

from .models import ChatRequest
from .chat_service import chat_stream
from .fallback_config import ai_enabled
from .fallback_handler import disabled_fallback

logger = logging.getLogger(__name__)

router = APIRouter(prefix="/chat", tags=["chat"])


@router.post("/sse")
async def chat_sse(req: ChatRequest):
    """
    SSE 流式对话。

    返回格式：
    - event: token → data: {"content": "..."}
    - event: tool_call → data: {"tool": "...", "args": {...}}
    - event: tool_result → data: {"tool": "...", "result": "..."}
    - event: done → data: null
    - event: error → data: {"message": "..."}
    """
    if not req.query.strip():
        raise HTTPException(status_code=400, detail="query 不能为空")

    logger.info(
        "chat_sse userId=%s sessionId=%s query_len=%d",
        req.userId[:3] + "***",
        req.sessionId or "(new)",
        len(req.query),
    )

    # ── 降级开关检查 ──
    if not ai_enabled():
        logger.info("AI 导购已关闭，返回固定文案")
        return EventSourceResponse(
            disabled_fallback(),
            media_type="text/event-stream",
            headers={
                "Cache-Control": "no-cache",
                "Connection": "keep-alive",
                "X-Accel-Buffering": "no",
            },
        )

    return EventSourceResponse(
        chat_stream(
            query=req.query,
            user_id=req.userId,
            session_id=req.sessionId,
        ),
        media_type="text/event-stream",
        headers={
            "Cache-Control": "no-cache",
            "Connection": "keep-alive",
            "X-Accel-Buffering": "no",
        },
    )