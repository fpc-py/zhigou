"""
智购 AI Orchestrator — API 路由
"""

import logging

from fastapi import APIRouter, HTTPException
from fastapi.responses import PlainTextResponse
from fastapi import Header
from sse_starlette.sse import EventSourceResponse

from .metrics import render as render_metrics
from .metrics import record_chat, record_sse_close, record_sse_event, record_sse_open

from .models import ChatRequest
from .chat_service import chat_stream, clear_session
from .fallback_config import ai_enabled
from .fallback_handler import disabled_fallback

logger = logging.getLogger(__name__)

router = APIRouter(prefix="/chat", tags=["chat"])
reminders_router = APIRouter(prefix="/reminders", tags=["reminders"])


@reminders_router.get("/mine")
async def reminders_mine(unreadOnly: bool = False, x_user_id: str | None = Header(default=None)):
    """本人提醒列表（AI 主动提醒中心，块2）。"""
    from .reminders import mine, touch_user
    uid = x_user_id or "0"
    touch_user(uid)
    return {"data": mine(uid, unread_only=unreadOnly)}


@reminders_router.post("/scan")
async def reminders_scan(x_user_id: str | None = Header(default=None)):
    """手动触发一次提醒扫描（当前用户）。"""
    from .reminders import run_once, touch_user
    uid = x_user_id or "0"
    touch_user(uid)
    counts = await run_once(uid)
    return {"data": counts}


@reminders_router.post("/read/{reminder_id}")
async def reminders_read(reminder_id: str, x_user_id: str | None = Header(default=None)):
    """标记提醒已读。"""
    from .reminders import mark_read
    r = mark_read(x_user_id or "0", reminder_id)
    if not r:
        raise HTTPException(status_code=404, detail="提醒不存在或不属于当前用户")
    return {"data": r}

closet_router = APIRouter(prefix="/closet", tags=["closet"])


@closet_router.post("/scan")
async def closet_scan(req: dict, x_user_id: str | None = Header(default=None)):
    """AI 衣物识别录入：图片 URL → LLM 视觉识别 → 自动加入衣橱（衣橱零录入 H5 闭环）。"""
    from .tools import _current_user_id, closet_scan_item
    if not x_user_id:
        raise HTTPException(status_code=401, detail="缺少用户身份")
    image_url = (req or {}).get("image_url", "").strip()
    if not image_url:
        raise HTTPException(status_code=422, detail="image_url 必填")
    _current_user_id.set(x_user_id)
    try:
        message = await closet_scan_item.ainvoke({"image_url": image_url})
        return {"code": 200, "message": "success", "data": {"message": message}}
    except PermissionError as e:
        raise HTTPException(status_code=403, detail=str(e))
    except Exception as e:
        logger.warning("closet scan 失败: %s", e)
        return {"code": 500, "message": "AI 识别失败，请稍后重试", "data": None}

metrics_router = APIRouter(prefix="", tags=["metrics"])


@metrics_router.get("/metrics")
async def metrics():
    """Prometheus 文本格式指标（AI 链路可观测，块6）。"""
    return PlainTextResponse(render_metrics(), media_type="text/plain; version=0.0.4")


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
    record_chat()
    if not ai_enabled():
        logger.info("AI 导购已关闭，返回固定文案")
        return EventSourceResponse(
            _sse_counted(disabled_fallback()),
            media_type="text/event-stream",
            headers={
                "Cache-Control": "no-cache",
                "Connection": "keep-alive",
                "X-Accel-Buffering": "no",
            },
        )

    return EventSourceResponse(
        _sse_counted(
            chat_stream(
                query=req.query,
                user_id=req.userId,
                session_id=req.sessionId,
                image_url=req.imageUrl or "",
            )
        ),
        media_type="text/event-stream",
        headers={
            "Cache-Control": "no-cache",
            "Connection": "keep-alive",
            "X-Accel-Buffering": "no",
        },
    )


async def _sse_counted(events):
    """SSE 生成器包装：活跃连接数 / 累计事件数（块6 可观测埋点）。"""
    record_sse_open()
    try:
        async for ev in events:
            record_sse_event()
            yield ev
    finally:
        record_sse_close()
