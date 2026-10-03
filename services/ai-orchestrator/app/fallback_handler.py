"""
智购 AI Orchestrator — 降级处理模块

提供两种降级场景：
1. AI 主动关闭 → 固定文案 SSE
2. LLM 调用超时 → 兜底推荐商品卡片（走 product-service 推荐接口）

SSE 事件统一以 dict 形式 yield（{"event": ..., "data": ...}），
交由 sse_starlette 序列化为标准格式 `event: xxx\ndata: {...}`，
前端 sse-polyfill 依赖标准格式解析事件名。
"""

import json
import logging
from typing import AsyncGenerator

import httpx

from .config import settings

logger = logging.getLogger(__name__)

# ── 固定文案（ai.enabled=false 时返回） ──

DISABLED_MESSAGE = "AI 助手繁忙，请浏览首页推荐～"


async def disabled_fallback() -> AsyncGenerator[dict, None]:
    """ai.enabled=false 时，直接返回固定文案 SSE 事件流。"""
    yield {"event": "token", "data": json.dumps({"content": DISABLED_MESSAGE}, ensure_ascii=False)}
    yield {"event": "done", "data": "null"}


# ── LLM 超时降级：推荐商品卡片 ──

RECOMMEND_CARD_TEMPLATE = """
亲，我暂时没法为你智能回答，先看看这些推荐商品吧～

{cards}

也可以换个问题再试试～
"""

PRODUCT_CARD_TEMPLATE = """┌ {name}
├ 价格: ¥{price:.2f}
├ 库存: {stock}
└ 去看看: 商品页搜索「{name}」"""


async def _fetch_recommended_products(user_id: str) -> list[dict]:
    """
    调用 product-service 获取推荐商品列表。

    product-service 要求认证，内网调用通过 x-user-id 透传身份（原实现未带头部，会 403）。
    失败时返回空列表。
    """
    try:
        url = f"{settings.product_service_url}/product/page?pageNum=1&pageSize=3"
        headers = {"x-user-id": user_id} if user_id else {}
        async with httpx.AsyncClient(timeout=3.0) as client:
            resp = await client.get(url, headers=headers)
            resp.raise_for_status()
            data = resp.json()
            return data.get("data", {}).get("records", [])
    except Exception as e:
        logger.warning("推荐商品获取失败: %s", e)
        return []


def _format_card(product: dict) -> str:
    """将单个商品格式化为卡片文本。"""
    name = product.get("name", "未知商品")
    price_fen = product.get("priceMin", 0) or 0
    return PRODUCT_CARD_TEMPLATE.format(
        name=name,
        price=price_fen / 100.0,
        stock="有货",
    )


async def timeout_fallback_stream(user_id: str = "") -> AsyncGenerator[dict, None]:
    """
    LLM 超时降级：获取推荐商品并格式化为结构化卡片 SSE 流。
    """
    products = await _fetch_recommended_products(user_id)

    if not products:
        # 连推荐接口也挂了，返回简洁降级文案
        msg = "AI 助手暂时忙不过来，请稍后再试～"
        yield {"event": "token", "data": json.dumps({"content": msg}, ensure_ascii=False)}
        yield {"event": "done", "data": "null"}
        return

    cards_text = "\n\n".join(_format_card(p) for p in products)
    full_text = RECOMMEND_CARD_TEMPLATE.format(cards=cards_text).strip()

    yield {"event": "token", "data": json.dumps({"content": full_text}, ensure_ascii=False)}
    yield {
        "event": "tool_call",
        "data": json.dumps({"tool": "__fallback__", "args": {"reason": "llm_timeout"}}, ensure_ascii=False),
    }
    yield {"event": "done", "data": "null"}
