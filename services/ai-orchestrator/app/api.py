"""
智购 AI Orchestrator — API 路由
"""

import logging

from fastapi import APIRouter, HTTPException
from sse_starlette.sse import EventSourceResponse

from .models import ChatRequest
from .chat_service import chat_stream, clear_session
from .fallback_config import ai_enabled
from .fallback_handler import disabled_fallback

logger = logging.getLogger(__name__)

router = APIRouter(prefix="/chat", tags=["chat"])


@router.delete("/session/{session_id}")
async def chat_session_delete(session_id: str):
    """
    清空指定会话（删除持久化历史，下一次对话从零开始）。
    """
    ok = clear_session(session_id)
    if ok:
        return {"code": 200, "message": "会话已清空"}
    raise HTTPException(status_code=404, detail="会话不存在或已清空")


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