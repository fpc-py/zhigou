"""
智购 AI Orchestrator — LangChain 工具集

每个工具内部校验当前请求的 userId 与入参 userId 是否一致，
不一致返回 403 错误信息。调用后端服务失败时返回友好提示。
"""

import json
import logging
from contextvars import ContextVar
from typing import Any, Optional

import httpx
from langchain_core.tools import tool

from .config import settings

logger = logging.getLogger(__name__)

# ── 上下文：当前请求的用户 ID，在 chat_service 中设置 ──
_current_user_id: ContextVar[Optional[str]] = ContextVar("current_user_id", default=None)


def _get_current_user() -> str:
    uid = _current_user_id.get()
    if uid is None:
        raise RuntimeError("current_user_id 未设置 — 确保在请求上下文中调用")
    return uid


def _check_user_id(param_user_id: str) -> None:
    """工具入参 userId 必须等于当前请求 userId，否则抛出 403。"""
    current = _get_current_user()
    if current != param_user_id:
        logger.warning("userId 越权: request=%s, param=%s", current, param_user_id)
        raise PermissionError(f"userId 不一致: 当前请求用户 {current}，工具入参 {param_user_id}")


async def _http_get(url: str, timeout: float = 5.0) -> dict[str, Any]:
    """执行 HTTP GET 并返回 JSON body。"""
    async with httpx.AsyncClient(timeout=timeout) as client:
        resp = await client.get(url)
        resp.raise_for_status()
        return resp.json()


async def _http_post(url: str, json_data: dict, timeout: float = 5.0) -> dict[str, Any]:
    """执行 HTTP POST 并返回 JSON body。"""
    async with httpx.AsyncClient(timeout=timeout) as client:
        resp = await client.post(url, json=json_data)
        resp.raise_for_status()
        return resp.json()


# ── 工具函数 ──


@tool
async def search_products(keyword: str, limit: int = 10) -> str:
    """
    搜索商品。根据关键词搜索匹配的商品列表。
    返回商品名称、价格、库存状态摘要。

    Args:
        keyword: 搜索关键词
        limit: 返回条数，默认 10，最大 50
    """
    try:
        url = f"{settings.search_service_url}/api/products/search?keyword={keyword}&pageSize={limit}"
        data = await _http_get(url, timeout=5.0)
        items = data.get("data", {}).get("records", [])
        if not items:
            return "未搜索到相关商品。"
        results = []
        for item in items[:limit]:
            results.append(f"· {item.get('name', '未知')} | ¥{item.get('price', 0)/100:.2f} | {item.get('stockStatus', '未知')}")
        return "\n".join(results)
    except Exception as e:
        logger.warning("search_products 调用失败: %s", e)
        return "这项信息暂时没查到"


@tool
async def get_price(sku_id: str) -> str:
    """
    查询商品最新价格。

    Args:
        sku_id: SKU ID
    """
    try:
        url = f"{settings.product_service_url}/api/products/sku/{sku_id}/price"
        data = await _http_get(url, timeout=5.0)
        price_fen = data.get("data", {}).get("price", 0)
        return f"¥{price_fen/100:.2f}"
    except Exception as e:
        logger.warning("get_price 调用失败 skuId=%s: %s", sku_id, e)
        return "这项信息暂时没查到"


@tool
async def check_inventory(sku_id: str) -> str:
    """
    查询商品库存情况。

    Args:
        sku_id: SKU ID
    """
    try:
        url = f"{settings.inventory_service_url}/api/inventory/{sku_id}"
        data = await _http_get(url, timeout=5.0)
        inv = data.get("data", {})
        available = inv.get("available", 0)
        status = inv.get("status", "unknown")
        return f"可售库存: {available}，状态: {status}"
    except Exception as e:
        logger.warning("check_inventory 调用失败 skuId=%s: %s", sku_id, e)
        return "这项信息暂时没查到"


@tool
async def get_user_profile(user_id: str) -> str:
    """
    获取用户画像信息。包括偏好标签、消费等级、最近浏览品类。

    Args:
        user_id: 用户 ID
    """
    try:
        # 安全校验：userId 越权则返回友好提示
        _check_user_id(user_id)

        url = f"{settings.user_service_url}/api/users/{user_id}/profile"
        data = await _http_get(url, timeout=5.0)
        profile = data.get("data", {})
        return json.dumps(profile, ensure_ascii=False)
    except PermissionError:
        return "这项信息暂时没查到"
    except Exception as e:
        logger.warning("get_user_profile 调用失败 userId=%s: %s", user_id, e)
        return "这项信息暂时没查到"


@tool
async def apply_coupon(user_id: str, items: list[str]) -> str:
    """
    为用户推荐可用优惠券。

    Args:
        user_id: 用户 ID
        items: 商品 SKU ID 列表
    """
    try:
        # 安全校验：userId 越权则返回友好提示
        _check_user_id(user_id)

        url = f"{settings.marketing_service_url}/api/coupons/available"
        payload = {"userId": user_id, "skuIds": items}
        data = await _http_post(url, json_data=payload, timeout=5.0)
        coupons = data.get("data", [])
        if not coupons:
            return "当前暂无可用优惠券。"
        results = []
        for c in coupons:
            results.append(f"· {c.get('name', '优惠券')} | 减¥{c.get('discount', 0)/100:.0f} | 有效期至 {c.get('endTime', '未知')}")
        return "\n".join(results)
    except PermissionError:
        return "这项信息暂时没查到"
    except Exception as e:
        logger.warning("apply_coupon 调用失败 userId=%s: %s", user_id, e)
        return "这项信息暂时没查到"


# ── 工具注册表 ──

TOOLS = [
    search_products,
    get_price,
    check_inventory,
    get_user_profile,
    apply_coupon,
]