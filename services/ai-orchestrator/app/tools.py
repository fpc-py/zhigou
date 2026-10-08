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


async def _http_get(url: str, timeout: float = 5.0, headers: dict | None = None) -> dict[str, Any]:
    """执行 HTTP GET 并返回 JSON body。"""
    async with httpx.AsyncClient(timeout=timeout) as client:
        resp = await client.get(url, headers=headers or {})
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
        limit = min(limit, 50)
        url = f"{settings.product_service_url}/product/page?keyword={keyword}&pageNum=1&pageSize={limit}"
        # product-service 要求认证，内网调用通过 x-user-id 透传身份
        headers = {"x-user-id": _get_current_user()}
        data = await _http_get(url, timeout=5.0, headers=headers)
        items = data.get("data", {}).get("records", [])
        if not items:
            return "未搜索到相关商品。"
        results = []
        for item in items[:limit]:
            price_min = item.get("priceMin", 0)
            skus = item.get("skus") or []
            # 带上首个 SKU 的真实 skuId，便于 LLM 后续调用查价/查库存工具
            sku_hint = f" | skuId={skus[0].get('skuId')}" if skus else ""
            results.append(f"· {item.get('name', '未知')} | ¥{price_min / 100:.2f} 起{sku_hint}")
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
        url = f"{settings.product_service_url}/product/sku/{sku_id}"
        headers = {"x-user-id": _get_current_user()}
        data = await _http_get(url, timeout=5.0, headers=headers)
        sku = data.get("data") or {}
        if not sku:
            return "未查询到该 SKU 的价格信息。"
        price_fen = sku.get("price", 0)
        return f"¥{price_fen / 100:.2f}"
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
        url = f"{settings.inventory_service_url}/inventory/{sku_id}"
        headers = {"x-user-id": _get_current_user()}
        data = await _http_get(url, timeout=5.0, headers=headers)
        inv = data.get("data")
        if not inv:
            return "该商品暂无库存记录。"
        available = inv.get("available", 0)
        locked = inv.get("locked", 0)
        return f"可售库存: {available}，已锁定: {locked}"
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

        url = f"{settings.user_service_url}/user/profile"
        headers = {"x-user-id": user_id}
        data = await _http_get(url, timeout=5.0, headers=headers)
        profile = data.get("data")
        if not profile:
            return "暂无该用户画像信息。"
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

        url = f"{settings.marketing_service_url}/coupon/mine?userId={user_id}&status=UNUSED"
        data = await _http_get(url, timeout=5.0)
        coupons = data.get("data", [])
        if not coupons:
            return "当前暂无可用优惠券。"
        results = []
        for c in coupons:
            results.append(f"· 优惠券 #{c.get('couponTemplateId', '未知')} | 状态 {c.get('status', '未知')}")
        return "\n".join(results)
    except PermissionError:
        return "这项信息暂时没查到"
    except Exception as e:
        logger.warning("apply_coupon 调用失败 userId=%s: %s", user_id, e)
        return "这项信息暂时没查到"


# ── P1 决策辅助工具：需求拆解 / 比价 / 避坑 ──

# 品类词表（关键词 → 品类名）
_CATEGORY_WORDS: dict[str, str] = {
    "手机": "手机", "phone": "手机", "iphone": "手机",
    "电脑": "笔记本电脑", "笔记本": "笔记本电脑", "laptop": "笔记本电脑",
    "耳机": "耳机", "蓝牙耳机": "耳机", "降噪": "耳机",
    "鞋": "运动鞋", "跑鞋": "运动鞋", "跑步鞋": "运动鞋",
    "外套": "外套", "羽绒服": "外套", "大衣": "外套",
    "衣服": "服装", "衬衫": "服装", "T恤": "服装",
    "手表": "手表", "智能手表": "手表", "手环": "手表",
    "音箱": "音箱", "音响": "音箱",
    "充电宝": "充电宝", "数据线": "配件", "手机壳": "配件",
    "平板": "平板电脑", "ipad": "平板电脑",
    "相机": "相机", "摄像头": "相机",
    "家电": "家电", "冰箱": "家电", "洗衣机": "家电", "空调": "家电",
    "扫地机": "扫地机器人", "吸尘器": "吸尘器",
    "护肤": "护肤品", "化妆品": "护肤品", "香水": "护肤品",
    "玩具": "玩具", "乐高": "玩具",
    "包包": "包", "背包": "包", "行李箱": "箱包",
    "水杯": "水杯", "保温杯": "水杯",
}

# 场景词表
_SCENE_WORDS: dict[str, str] = {
    "送人": "送礼", "送朋友": "送礼", "送女友": "送礼", "送女朋友": "送礼",
    "送爸妈": "送礼", "礼物": "送礼", "纪念日": "送礼", "生日": "送礼",
    "办公": "办公", "上班": "办公", "开会": "办公",
    "运动": "运动", "健身": "运动", "跑步": "运动", "锻炼": "运动",
    "通勤": "通勤", "地铁": "通勤",
    "居家": "居家", "家用": "居家", "宿舍": "居家",
    "旅行": "旅行", "出差": "旅行",
    "学习": "学习", "考研": "学习", "网课": "学习",
}

# 偏好词表
_PREFERENCE_WORDS: dict[str, str] = {
    "性价比": "性价比", "实惠": "性价比", "划算": "性价比", "便宜": "性价比",
    "高端": "高端", "旗舰": "高端", "顶配": "高端",
    "轻便": "轻便", "便携": "轻便", "小巧": "轻便",
    "耐用": "耐用", "结实": "耐用", "抗造": "耐用",
    "静音": "静音", "降噪": "静音",
    "好看": "颜值", "颜值": "颜值", "美观": "颜值",
    "大容量": "大容量", "续航": "续航", "待机": "续航",
}


def _match_word(message: str, table: dict[str, str]) -> str | None:
    """在消息中匹配词表，返回第一个命中。"""
    for kw, label in table.items():
        if kw in message:
            return label
    return None


@tool
async def analyze_requirement(message: str) -> str:
    """
    需求拆解。把用户的模糊购物需求拆成结构化要素：预算、品类、场景、偏好。
    用户需求含糊（只说想买什么东西、用途、价位带，没指明具体商品）时调用，
    拆解结果用于后续组合搜索与推荐。

    Args:
        message: 用户的原始需求描述
    """
    try:
        import re
        budget = None
        m = re.search(r"(\d+)\s*[块元]", message)
        if m:
            budget = int(m.group(1))

        result = {
            "budget": budget,
            "category": _match_word(message, _CATEGORY_WORDS),
            "scene": _match_word(message, _SCENE_WORDS),
            "preference": _match_word(message, _PREFERENCE_WORDS),
            "keywords": " ".join(
                str(w) for w in (budget, _match_word(message, _CATEGORY_WORDS), _match_word(message, _SCENE_WORDS))
                if w is not None
            ),
        }
        return json.dumps(result, ensure_ascii=False)
    except Exception as e:
        logger.warning("analyze_requirement 解析失败: %s", e)
        return json.dumps({"budget": None, "category": None, "scene": None, "preference": None, "keywords": message}, ensure_ascii=False)


@tool
async def compare_prices(sku_ids: list[str]) -> str:
    """
    比价。对多个 SKU 逐一查最新价格、规格、库存，生成横向对比清单。
    用户问"哪个划算""哪款性价比高""对比一下几款"时调用。

    Args:
        sku_ids: 要对比的 SKU ID 列表（2~5 个）
    """
    try:
        if not sku_ids:
            return "没有可对比的 SKU。"
        rows = []
        for sid in sku_ids[:5]:
            try:
                url = f"{settings.product_service_url}/product/sku/{sid}"
                headers = {"x-user-id": _get_current_user()}
                data = await _http_get(url, timeout=5.0, headers=headers)
                sku = data.get("data") or {}
                price_fen = sku.get("price", 0)
                spec = sku.get("specValue") or sku.get("specName") or "默认规格"
                stock = sku.get("stock", 0)
                stock_txt = "有货" if stock > 10 else ("紧张" if stock > 0 else "缺货")
                rows.append(f"· SKU {sid} | ¥{price_fen / 100:.2f} | 规格 {spec} | 库存 {stock_txt}")
            except Exception as e:
                logger.warning("compare_prices 查 SKU %s 失败: %s", sid, e)
                rows.append(f"· SKU {sid} | 价格信息暂时查不到")
        if not rows:
            return "这项信息暂时没查到，稍后再试试？"
        return "价格/规格对比：\n" + "\n".join(rows)
    except Exception as e:
        logger.warning("compare_prices 调用失败: %s", e)
        return "这项信息暂时没查到"


@tool
async def review_analysis(spu_id: str) -> str:
    """
    避坑 / 选购提醒。查看某商品的参数要点与选购注意点，帮助用户避坑。
    用户问"这个质量怎么样""有什么坑吗""值得买吗"时调用。

    Args:
        spu_id: 商品 SPU ID
    """
    try:
        url = f"{settings.product_service_url}/product/{spu_id}"
        headers = {"x-user-id": _get_current_user()}
        data = await _http_get(url, timeout=5.0, headers=headers)
        spu = data.get("data") or {}
        if not spu:
            return "该商品信息暂时查不到。"
        name = spu.get("name", "该商品")
        skus = spu.get("skus") or []
        sku_count = len(skus)
        price_min = spu.get("priceMin", 0) or 0
        price_max = spu.get("priceMax", 0) or price_min
        stock_total = sum((s.get("stock") or 0) for s in skus)

        lines = [f"{name} 选购提醒：", f"· 价格区间 ¥{price_min / 100:.2f} ~ ¥{price_max / 100:.2f}"]
        lines.append(f"· 规格数 {sku_count} 个，总库存 {stock_total} 件")
        if stock_total <= 0:
            lines.append("· 当前缺货，建议先看替代款")
        elif stock_total < 10:
            lines.append("· 库存紧张，有意向尽早下单")
        if sku_count > 1:
            lines.append("· 多规格商品建议先确认自己要的规格是否有货")
        # 评价数据尚未接入：如实提示，不编造口碑
        lines.append("· 差评/口碑分析待评价数据接入后提供，当前可参考页面参数说明")
        return "\n".join(lines)
    except Exception as e:
        logger.warning("review_analysis 调用失败 spuId=%s: %s", spu_id, e)
        return "这项信息暂时没查到"


# ── 工具注册表 ──

TOOLS = [
    search_products,
    get_price,
    check_inventory,
    get_user_profile,
    apply_coupon,
    analyze_requirement,
    compare_prices,
    review_analysis,
]