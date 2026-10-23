"""
智购 AI Orchestrator — LangChain 工具集

每个工具内部校验当前请求的 userId 与入参 userId 是否一致，
不一致返回 403 错误信息。调用后端服务失败时返回友好提示。
"""

import asyncio
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


async def _http_get(
    url: str,
    timeout: float | None = None,
    headers: dict | None = None,
    retry: bool = True,
) -> dict[str, Any]:
    """执行 HTTP GET 并返回 JSON body。

    工作流2 可靠性治理：
    - 超时默认取 settings.ai_tool_timeout_s（5s），可通过 ZHIGOU_AI_TOOL_TIMEOUT_S 覆盖；
    - 只读 GET 自动重试 settings.ai_tool_retries 次（默认 1），5xx/网络错误重试、4xx 业务错误不重试；
    - 重试间隔按尝试次数线性递增（0.3s / 0.6s …），避免惊群。
    """
    timeout = settings.ai_tool_timeout_s if timeout is None else timeout
    max_attempts = settings.ai_tool_retries + 1 if retry else 1
    last_err: Exception | None = None
    for attempt in range(max_attempts):
        try:
            async with httpx.AsyncClient(timeout=timeout) as client:
                resp = await client.get(url, headers=headers or {})
                resp.raise_for_status()
                return resp.json()
        except httpx.HTTPStatusError as e:
            # 4xx 是业务错误（参数/鉴权/不存在），重试无意义 → 直接抛；5xx 属于服务端瞬时故障 → 重试
            if e.response is not None and e.response.status_code < 500:
                raise
            last_err = e
        except (httpx.TransportError, httpx.TimeoutException) as e:
            last_err = e
        if attempt < max_attempts - 1:
            await asyncio.sleep(settings.ai_tool_retry_delay_s * (attempt + 1))
    raise last_err or RuntimeError("_http_get unreachable")


async def _http_post(
    url: str,
    json_data: dict,
    timeout: float | None = None,
    headers: dict | None = None,
) -> dict[str, Any]:
    """执行 HTTP POST 并返回 JSON body。

    写操作一律不自动重试（重试可能导致重复下单/重复入账），超时失败交由上层降级处理。
    """
    timeout = settings.ai_tool_timeout_s if timeout is None else timeout
    async with httpx.AsyncClient(timeout=timeout) as client:
        resp = await client.post(url, json=json_data, headers=headers or {})
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
        data = await _http_get(url, headers=headers)
        items = data.get("data", {}).get("records", [])
        if not items:
            return "未搜索到相关商品。"
        results = []
        for item in items[:limit]:
            price_min = item.get("priceMin", 0)
            skus = item.get("skus") or []
            # 同时带上 spuId 与首个 SKU 的 skuId：spuId 用于评价分析(review_analysis)、
            # skuId 用于查价/查库存(get_price/check_inventory)，避免 LLM 拿错 ID 类型
            spu_id = item.get("spuId")
            spu_hint = f" | spuId={spu_id}" if spu_id else ""
            sku_hint = f" | skuId={skus[0].get('skuId')}" if skus else ""
            results.append(f"· {item.get('name', '未知')} | ¥{price_min / 100:.2f} 起{spu_hint}{sku_hint}")
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
        data = await _http_get(url, headers=headers)
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
        data = await _http_get(url, headers=headers)
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
        data = await _http_get(url, headers=headers)
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
        data = await _http_get(url)
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
    跨平台比价。对多个 SKU 聚合京东/天猫/拼多多等多渠道报价，计算最优购买方案。
    用户问"哪个平台便宜""哪里买划算""全网比价""对比各平台价格"时调用。

    Args:
        sku_ids: 要对比的 SKU ID 列表（2~5 个）
    """
    try:
        if not sku_ids:
            return "没有可对比的 SKU。"
        headers = {"x-user-id": _get_current_user()}
        data = await _http_post(
            f"{settings.product_service_url}/price/compare",
            json_data=[s for s in sku_ids[:5]],
            timeout=8.0,
            headers=headers,
        )
        rows = data.get("data") or []
        if not rows:
            return "这项信息暂时没查到，稍后再试试？"
        lines = []
        for item in rows:
            lines.append(f"· {item.get('skuName', item.get('skuId'))}（SKU {item.get('skuId')}）")
            for offer in item.get("offers", []):
                best = " ★最优" if offer.get("isBest") else ""
                lines.append(
                    f"  - {offer.get('source')} ¥{offer.get('totalPrice', 0) / 100:.2f}"
                    f"（售价 ¥{offer.get('price', 0) / 100:.2f} + 运费 ¥{offer.get('shippingFee', 0) / 100:.2f}"
                    f"）{offer.get('deliveryDays', '-')}天到货{best}"
                )
            lines.append(f"  → 建议：{item.get('suggestion', '')}")
        return "跨平台比价（含运费与到货时间）：\n" + "\n".join(lines)
    except Exception as e:
        logger.warning("compare_prices 调用失败: %s", e)
        return "这项信息暂时没查到"



@tool
async def review_analysis(spu_id: str) -> str:
    """
    避坑 / 选购提醒。查看某商品的真实用户评价（评分/星级分布/差评要点）与选购注意点，
    并用规则识别刷评/水军风险，帮助用户避坑。用户问"这个质量怎么样""有什么坑吗"
    "值得买吗""评价怎么样""口碑好吗"时调用。

    Args:
        spu_id: 商品 SPU ID
    """
    try:
        headers = {"x-user-id": _get_current_user()}
        # 1) SPU 基础信息（入参兼容：若 LLM 传的是 skuId，直查 SPU 失败则按 skuId 反查）
        data = await _http_get(f"{settings.product_service_url}/product/{spu_id}", headers=headers)
        spu = data.get("data") or {}
        if not spu:
            try:
                sku_data = await _http_get(
                    f"{settings.product_service_url}/product/sku/{spu_id}",
                    headers=headers,
                )
                sku_info = sku_data.get("data") or {}
                real_spu = sku_info.get("spuId")
                if real_spu:
                    spu_id = str(real_spu)
                    data = await _http_get(f"{settings.product_service_url}/product/{spu_id}", headers=headers)
                    spu = data.get("data") or {}
            except Exception as e:
                logger.warning("review_analysis 反查 SPU 失败 skuId=%s: %s", spu_id, e)
        if not spu:
            return "该商品信息暂时查不到。"
        name = spu.get("name", "该商品")
        skus = spu.get("skus") or []
        stock_total = sum((s.get("stock") or 0) for s in skus)

        # 2) 真实评价统计
        stats_data = await _http_get(
            f"{settings.product_service_url}/product/review/stats?spuId={spu_id}",
            headers=headers,
        )
        stats = stats_data.get("data") or {}
        total = int(stats.get("total") or 0)
        avg = stats.get("avgRating") or 0
        dist = stats.get("ratingDist") or {}
        low_star = int(stats.get("lowStarCount") or 0)
        dup_cnt = int(stats.get("duplicateCount") or 0)

        lines = [f"{name} 真实评价分析："]
        if total <= 0:
            lines.append("· 该商品暂无评价，口碑参考有限，可结合参数说明判断")
        else:
            lines.append(f"· 共 {total} 条评价，均分 {avg}/5.0")
            five = int(dist.get("5", 0) or 0)
            four = int(dist.get("4", 0) or 0)
            lines.append(f"· 星级分布：5★x{five}  4★x{four}  3★及以下x{low_star}")
            if low_star > 0:
                # 3) 拉差评要点（3 星及以下，最多 5 条）
                low_data = await _http_get(
                    f"{settings.product_service_url}/product/review?spuId={spu_id}"
                    f"&minRating=1&maxRating=3&pageSize=5",
                    headers=headers,
                )
                low_records = (low_data.get("data") or {}).get("records") or []
                if low_records:
                    lines.append("· 差评要点：")
                    for r in low_records[:3]:
                        content = (r.get("content") or "").strip()[:40]
                        lines.append(f"  - {content}")
            # 4) 水军/刷评风险（规则识别，非 LLM 编造）
            if dup_cnt >= 2:
                lines.append(f"· ⚠ 疑似刷评：发现 {dup_cnt} 条内容完全相同的评价，请理性看待")
            low_ratio = low_star * 100.0 / total
            if low_ratio >= 30:
                lines.append(f"· ⚠ 差评比例偏高（{low_ratio:.0f}%），下单前请重点确认上述差评要点")
            if total >= 10 and low_ratio < 15 and five * 100.0 / total >= 80:
                lines.append("· 整体口碑较好，可结合自身需求决策")

        # 5) 库存提示（保留原能力）
        if stock_total <= 0:
            lines.append("· 当前缺货，建议先看替代款")
        elif stock_total < 10:
            lines.append("· 库存紧张，有意向尽早下单")
        return "\n".join(lines)
    except Exception as e:
        logger.warning("review_analysis 调用失败 spuId=%s: %s", spu_id, e)
        return "这项信息暂时没查到"


@tool
async def analyze_user_context() -> str:
    """
    隐性需求挖掘。分析当前用户的画像底座（收藏 + 浏览历史）与历史订单，
    提炼购买偏好：常购品类、常用价位带、复购倾向。
    用户在表达需求时没说清偏好，或说"参考我买过的""我之前买过""跟上次差不多"时调用，
    用于补全隐性约束，让推荐更贴合用户历史习惯。用户身份由服务端注入，无需传参。
    """
    try:
        user_id = _get_current_user()
        headers = {"x-user-id": user_id}

        # ① 用户画像底座（收藏 + 浏览历史 + 偏好聚合；user-service 内网直连已放行）
        insight: dict | None = None
        try:
            url = f"{settings.user_service_url}/user/insight"
            data = await _http_get(url, headers=headers)
            insight = data.get("data") or None
        except Exception as e:
            logger.warning("analyze_user_context 画像底座不可用 userId=%s: %s", user_id, e)

        # ② 历史订单统计
        url = f"{settings.order_service_url}/order/mine?userId={user_id}"
        data = await _http_get(url, headers=headers)
        orders = data.get("data") or []

        if not orders and not insight:
            return "用户暂无历史行为（无订单 / 收藏 / 浏览），无法提炼购买偏好（推荐将按通用流程进行）。"

        category_count: dict[str, int] = {}
        price_points: list[int] = []
        total_items = 0
        for o in orders:
            amount = int(o.get("payAmount") or o.get("totalAmount") or 0)
            price_points.append(amount)
            for it in o.get("items") or []:
                name = it.get("skuName") or ""
                cnt = int(it.get("count") or 1)
                total_items += cnt
                cat = _match_word(name, _CATEGORY_WORDS)
                if cat:
                    category_count[cat] = category_count.get(cat, 0) + cnt

        # ③ 画像偏好并入（收藏/浏览品类 + 价位带 + 最近浏览）
        profile_categories: list[str] = (insight or {}).get("topCategories") or []
        profile_band = (insight or {}).get("priceBand")
        for cat in profile_categories:
            category_count[cat] = category_count.get(cat, 0) + 1
        recent_browse = [
            {
                "spuName": (it or {}).get("spuName"),
                "price": (it or {}).get("price"),
                "browseCount": (it or {}).get("browseCount"),
            }
            for it in ((insight or {}).get("recentBrowse") or [])[:3]
        ]

        # 价位带分桶
        def _bucket(fen: int) -> str:
            yuan = fen / 100
            if yuan < 100:
                return "百元内"
            if yuan < 500:
                return "100-500元"
            if yuan < 2000:
                return "500-2000元"
            if yuan < 5000:
                return "2000-5000元"
            return "5000元以上"

        buckets: dict[str, int] = {}
        for p in price_points:
            b = _bucket(p)
            buckets[b] = buckets.get(b, 0) + 1
        top_band = max(buckets, key=buckets.get) if buckets else profile_band

        top_cats = sorted(category_count.items(), key=lambda kv: -kv[1])[:3]
        result = {
            "orderCount": len(orders),
            "totalItems": total_items,
            "favoriteCount": (insight or {}).get("favoriteCount") or 0,
            "browseTotal": (insight or {}).get("browseTotal") or 0,
            "topCategories": [{"category": cat, "count": n} for cat, n in top_cats],
            "priceBand": top_band,
            "recentBrowse": recent_browse,
            "hint": "以上来自用户画像底座（收藏/浏览）与历史订单统计，可作为隐性需求补全依据（默认品类/价位带/复购倾向）；无历史时不编造",
        }
        return json.dumps(result, ensure_ascii=False)
    except PermissionError:
        return "这项信息暂时没查到"
    except Exception as e:
        logger.warning("analyze_user_context 调用失败 userId=%s: %s", user_id, e)
        return "这项信息暂时没查到"


# ── P1 第二批工具：凑单优化器 / 一键代下单 ──


def _fen_to_yuan(fen: int) -> str:
    return f"¥{max(int(fen or 0), 0) / 100:.2f}"


@tool
async def optimize_cart() -> str:
    """
    凑单优化器。拉取当前用户购物车与可用优惠券，调用满减引擎计算最优结算方案
    （原价合计、最优券、优惠明细、是否建议加购凑门槛）。用户问"怎么买最划算"
    "有什么优惠""凑单""用哪张券"时调用。用户身份由服务端注入，无需传参。
    """
    try:
        user_id = _get_current_user()

        # 1) 拉购物车（选中项）
        cart_headers = {"x-user-id": user_id}
        cart_data = await _http_get(f"{settings.cart_service_url}/cart/mine", headers=cart_headers)
        items = cart_data.get("data") or []
        selected = [i for i in items if i.get("selected", True)]
        if not selected:
            return "购物车是空的，先加点商品再帮您算优惠～"

        # 2) 逐 SKU 查现价与规格（cart 未存价格时兜底），组满减入参
        calc_items = []
        cart_lines = []
        for i in selected:
            sku_id = i.get("skuId")
            if not sku_id:
                continue
            count = int(i.get("count", 1))
            price = int(i.get("priceAtAdd") or 0)
            spec = "默认规格"
            if price <= 0:
                try:
                    sku_data = await _http_get(
                        f"{settings.product_service_url}/product/sku/{sku_id}",
                        headers={"x-user-id": user_id},
                    )
                    sku_info = sku_data.get("data") or {}
                    price = int(sku_info.get("price") or 0)
                    sv = sku_info.get("specValue")
                    sn = sku_info.get("specName")
                    if sv:
                        spec = f"{sn} {sv}" if sn else str(sv)
                except Exception as e:
                    logger.warning("optimize_cart 查 SKU %s 现价失败: %s", sku_id, e)
            if price <= 0:
                return f"购物车中 SKU {sku_id} 价格缺失，暂时算不了优惠。"
            calc_items.append({"skuId": int(sku_id), "count": count, "price": price})
            cart_lines.append(f"· skuId={sku_id} | 规格 {spec} | x{count} | {_fen_to_yuan(price)}")
        if not calc_items:
            return "购物车商品信息不完整，暂时算不了优惠。"

        # 3) 无券基础满减
        base = await _http_post(
            f"{settings.marketing_service_url}/discount/calculate",
            {"userId": int(user_id), "items": calc_items},
            headers={"x-user-id": user_id},
        )
        base_data = base.get("data") or {}
        base_total = base_data.get("totalAmount") or 0
        base_discount = base_data.get("discountAmount") or 0

        # 4) 逐券试算，选最优
        coupons_data = await _http_get(
            f"{settings.marketing_service_url}/coupon/mine?userId={user_id}&status=UNUSED",
            headers={"x-user-id": user_id},
        )
        coupons = coupons_data.get("data") or []
        best = {"couponId": None, "save": 0}
        best_data = base_data
        for c in coupons:
            cid = c.get("id") or c.get("couponId")
            if cid is None:
                continue
            try:
                r = await _http_post(
                    f"{settings.marketing_service_url}/discount/calculate",
                    {"userId": int(user_id), "items": calc_items, "couponId": int(cid)},
                    headers={"x-user-id": user_id},
                )
                rd = r.get("data") or {}
                save = (rd.get("totalAmount") or 0) - (rd.get("finalAmount") or 0)
                if save > best["save"]:
                    best = {"couponId": int(cid), "save": save}
                    best_data = rd
            except Exception as e:
                logger.warning("optimize_cart 券 %s 试算失败: %s", cid, e)

        # 4) 组装结果
        total = best_data.get("totalAmount") or base_total
        discount = best_data.get("discountAmount") or base_discount
        final = best_data.get("finalAmount") or (total - discount)
        lines = [
            f"购物车共 {len(selected)} 件商品，当前最优结算方案：",
            "购物车明细：" + "；".join(cart_lines) if cart_lines else "购物车明细：-",
            f"· 原价合计 {_fen_to_yuan(total)}",
            f"· 优惠 {_fen_to_yuan(discount)}（明细: " + (
                ", ".join(f"{d.get('ruleName')} -{_fen_to_yuan(d.get('discountAmount'))}"
                          for d in (best_data.get("detail") or [])) or "无") + "）",
            f"· 实付 {_fen_to_yuan(final)}",
        ]
        if best["couponId"]:
            lines.append(f"· 最优券 #{best['couponId']}，再省 {_fen_to_yuan(best['save'])}")
            avail = best_data.get("availableCoupons") or []
            for ac in avail[:3]:
                lines.append(f"  - 券 #{ac.get('couponId')} 可省 {_fen_to_yuan(ac.get('saveAmount'))}")
        else:
            lines.append("· 当前无更优券可用（基础满减已计入）")
        lines.append("提示：差一点门槛时，可加购同品类小件商品触发更高档满减（可让我推荐凑单品）。")
        return "\n".join(lines)
    except PermissionError:
        return "这项信息暂时没查到"
    except Exception as e:
        logger.warning("optimize_cart 调用失败 userId=%s: %s", user_id, e)
        return "这项信息暂时没查到"


@tool
async def create_order(sku_items: list[dict], coupon_id: int | None = None) -> str:
    """
    一键代下单。在用户明确确认购买后，按指定 SKU 与数量下单（幂等 requestId 防重复）。
    注意：本工具只应在用户明确表达"买/下单/就要这个"时调用，下单前必须向用户复述
    商品与金额并取得确认。用户身份由服务端注入，无需传参。

    Args:
        sku_items: 商品列表 [{"skuId": 123, "count": 1}, ...]
        coupon_id: 优惠券 ID（可选，由凑单优化器给出）
    """
    try:
        user_id = _get_current_user()

        if not sku_items:
            return "没有要下单的商品。"
        if len(sku_items) > 5:
            return "一次最多下 5 个商品，请分批下单。"

        import uuid
        request_id = f"ai-{uuid.uuid4().hex[:16]}"
        body = {
            "requestId": request_id,
            "skuItems": [{"skuId": int(i.get("skuId")), "count": int(i.get("count", 1))} for i in sku_items],
        }
        if coupon_id:
            body["couponId"] = int(coupon_id)

        headers = {"Content-Type": "application/json", "x-user-id": user_id}
        async with httpx.AsyncClient(timeout=8.0) as client:
            resp = await client.post(
                f"{settings.order_service_url}/order/create",
                json=body,
                headers=headers,
            )
            resp.raise_for_status()
        data = resp.json().get("data") or {}
        order_id = data.get("orderId") or data.get("orderNo")
        if not order_id:
            return "下单失败，订单服务未返回订单号，请稍后重试。"
        lines = [
            "✅ 订单已创建，请尽快支付（超时自动关单）：",
            f"· 订单号 {order_id}",
            f"· 应付金额 {_fen_to_yuan(data.get('payAmount') or 0)}",
        ]
        if data.get("itemList"):
            lines.append("· 商品: " + ", ".join(f"{i.get('productName')}x{i.get('count')}" for i in data["itemList"]))
        return "\n".join(lines)
    except PermissionError:
        return "这项信息暂时没查到"
    except Exception as e:
        logger.warning("create_order 调用失败 userId=%s: %s", user_id, e)
        return "下单失败，请稍后重试"


@tool
async def recommend_products(scene: str = "home", limit: int = 6) -> str:
    """
    个性化推荐。基于当前用户的历史订单（品类/价位偏好）、购物车意向与商品真实评价口碑，
    返回带可解释理由的推荐清单。用户说"给我推荐""有什么适合我的""为我定制""猜你喜欢"
    或需要个性化选择建议时调用；可与 analyze_user_context（隐性需求挖掘）配合使用。
    用户身份由服务端注入，无需传参。

    Args:
        scene: 推荐场景，home（默认，首页猜你喜欢）/ cart（购物车凑单）/ detail（详情页相关）
        limit: 返回条数上限，默认 6，最大 20
    """
    try:
        user_id = _get_current_user()
        url = f"{settings.product_service_url}/recommend?scene={scene}&limit={limit}"
        headers = {"x-user-id": user_id}
        data = await _http_get(url, headers=headers)
        resp = data.get("data") or {}
        items = resp.get("items") or []
        if not items:
            return "暂无推荐（商品库为空或无上架商品）。"

        lines = [resp.get("sceneText") or "为你推荐："]
        for it in items:
            name = it.get("name") or "未知商品"
            price = _fen_to_yuan(it.get("priceMin") or 0)
            reasons = "；".join(it.get("reasons") or [])
            tags = " ".join(it.get("tags") or [])
            line = f"· {name} | {price} 起"
            if tags:
                line += f" [{tags}]"
            if reasons:
                line += f" — {reasons}"
            lines.append(line)
        lines.append("（推荐依据：历史订单 + 购物车 + 真实评价统计，可解释可追溯）")
        return "\n".join(lines)
    except Exception as e:
        logger.warning("recommend_products 调用失败: %s", e)
        return "这项信息暂时没查到"


@tool
async def search_by_image(image_url: str, limit: int = 5) -> str:
    """
    图片搜款（多模态）。输入商品图片 URL，先用视觉模型识别商品特征（品类/颜色/风格/关键属性），
    再基于特征搜索本平台商品。用户上传图片问"有没有同款/类似的""图片里的商品"时调用。

    Args:
        image_url: 商品图片 URL（file-service 上传返回的公开 URL）
        limit: 返回商品数量上限
    """
    try:
        if not image_url or not image_url.startswith(("http://", "https://")):
            return "请提供有效的图片地址。"
        # 视觉识别 → 结构化特征
        features = await _vision_extract(image_url)
        if not features or not features.get("keywords"):
            return "图片识别失败：暂时无法看清图中的商品，请换一张更清晰的图试试？"
        # 基于特征搜索真实商品
        keyword = features.get("keywords")[0]
        matched = await search_products.ainvoke({"keyword": keyword, "limit": limit})
        return (
            f"从图片中识别到：{features.get('category', '未知品类')}"
            f"（颜色 {features.get('colors', '未知')}，风格 {features.get('style', '未知')}）\n"
            f"基于特征「{keyword}」搜到以下商品（真实在售）：\n{matched}\n"
            f"（说明：图片识别仅供参考，商品以实际搜索结果为准）"
        )
    except Exception as e:
        logger.warning("search_by_image 调用失败: %s", e)
        return "这项信息暂时没查到"


async def _vision_extract(image_url: str, schema: str = "product") -> dict | None:
    """调 LLM 视觉模型提取特征（OpenAI 兼容 image_url）。60s 超时 + 一次重试，失败返回 None。

    schema 决定提取目标：product=商品特征（search_by_image 用）、closet=衣物属性（衣橱零录入用）。
    """
    from openai import AsyncOpenAI
    schema_prompt = _VISION_SCHEMA_PROMPT.get(schema, _VISION_SCHEMA_PROMPT["product"])
    last_err: Exception | None = None
    for attempt in (1, 2):
        try:
            client = AsyncOpenAI(base_url=settings.llm_base_url, api_key=settings.llm_api_key, timeout=60.0)
            resp = await client.chat.completions.create(
                model=settings.llm_model,
                temperature=0.2,
                max_tokens=300,
                messages=[{
                    "role": "user",
                    "content": [
                        {"type": "text", "text": schema_prompt},
                        {"type": "image_url", "image_url": {"url": image_url}},
                    ],
                }],
            )
            text = resp.choices[0].message.content or ""
            text = text.strip()
            if text.startswith("```"):
                text = text.strip("`").removeprefix("json").strip()
            parsed = json.loads(text)
            if parsed:
                return parsed
        except Exception as e:
            last_err = e
            logger.warning("视觉识别第 %d 次失败 %s: %s", attempt, image_url, e)
    logger.warning("视觉识别最终失败 %s: %s", image_url, last_err)
    return None


# ── 衣橱零录入 + AI 每日穿搭（多模态复用 _vision_extract，不造轮子） ──

_VISION_SCHEMA_PROMPT = {
    "product": "识别这张商品图，只输出 JSON：{'category': '品类', 'colors': ['颜色'], 'style': '风格', 'keywords': ['2-3个搜索关键词']}，不要解释。",
    "closet": "识别这张衣物照片，只输出 JSON：{'name': '衣物名称（简洁，如：白色圆领T恤）', 'category': '上装/下装/外套/鞋履/配饰 之一', 'season': '春夏秋冬/四季 之一', 'color': '主色调', 'tags': ['2-3个标签，如：休闲/通勤/棉质']}，不要解释。",
}


@tool
async def closet_scan_item(image_url: str) -> str:
    """
    衣橱零录入（多模态）：识别衣物照片并自动加入衣橱（免手填分类/季节/颜色）。
    用户上传衣物照片问"这件衣服放进衣橱/识别这件衣服/帮我录入衣橱"时调用。

    Args:
        image_url: 衣物图片 URL（file-service 上传返回的公开 URL）
    """
    try:
        if not image_url or not image_url.startswith(("http://", "https://")):
            return "请提供有效的图片地址。"
        feats = await _vision_extract(image_url, schema="closet")
        if not feats or not feats.get("name"):
            return "识别失败：暂时看不清这件衣服，请换一张更清晰的平铺照片试试？"
        uid = _get_current_user()
        body = {
            "userId": uid,
            "name": str(feats.get("name"))[:60],
            "category": str(feats.get("category") or "配饰"),
            "season": str(feats.get("season") or "四季"),
            "color": str(feats.get("color") or ""),
            "imageUrl": image_url,
            "tags": "、".join(str(t) for t in (feats.get("tags") or [])),
        }
        resp = await _http_post(
            f"{settings.closet_service_url}/closet/item",
            json_data=body,
            headers={"X-User-Id": uid},
        )
        item = resp.get("data") or {}
        if not item.get("id"):
            return "衣橱服务暂时不可用，请稍后再试。"
        return (
            f"已自动加入衣橱 ✅\n"
            f"· {item.get('name')}（{item.get('category')} · {item.get('season')} · {item.get('color') or '未识别颜色'}）\n"
            f"· 标签：{item.get('tags') or '无'}\n"
            f"识别结果来自 AI 视觉模型，如果分类不对，直接说「帮我改一下」，我帮你修正。"
        )
    except Exception as e:
        logger.warning("closet_scan_item 调用失败: %s", e)
        return "这件衣服暂时没能加入衣橱，稍后再试试？"


@tool
async def outfit_daily(user_id: str, occasion: str = "日常") -> str:
    """
    AI 每日穿搭：基于真实衣橱衣物 + 场合规则引擎推荐 + AI 搭配理由。
    用户问"今天穿什么/穿什么好/怎么搭配/穿搭建议/搭配推荐"时调用。

    Args:
        user_id: 当前用户 ID（服务端注入）
        occasion: 场合（日常/通勤/约会/运动/正式，默认日常）
    """
    try:
        _check_user_id(user_id)
        items = await _http_get(
            f"{settings.closet_service_url}/closet/items?userId={user_id}",
            headers={"X-User-Id": user_id},
        )
        item_list = items.get("data") or []
        if not item_list:
            return "你的衣橱还是空的——拍一件衣服照片发给我，我帮你一键录入，攒几件就能出穿搭。"
        rec = await _http_get(
            f"{settings.closet_service_url}/closet/outfit/recommend?userId={user_id}&occasion={occasion}",
            headers={"X-User-Id": user_id},
        )
        outfit = rec.get("data") or {}
        lines = [f"今日穿搭建议（{occasion}）· 基于你的真实衣橱 {len(item_list)} 件衣物："]
        if outfit.get("items"):
            for it in outfit["items"]:
                lines.append(
                    f"· {it.get('name')}（{it.get('color') or '未识别'} · {it.get('season') or '四季'}）"
                )
            if outfit.get("note"):
                lines.append(f"搭配说明：{outfit['note']}")
        else:
            lines.append("· 规则引擎暂未匹配到完整搭配，以下为衣橱可选项：")
            for it in item_list[:5]:
                lines.append(
                    f"  - {it.get('name')}（{it.get('category')} · {it.get('color') or ''}）"
                )
        # AI 润色（可选）：生成 1-2 句搭配理由；失败降级为规则结果，不阻塞
        try:
            names = "、".join(str(it.get("name")) for it in (outfit.get("items") or item_list[:5]))
            if names:
                from openai import AsyncOpenAI
                client = AsyncOpenAI(base_url=settings.llm_base_url, api_key=settings.llm_api_key, timeout=15.0)
                tip_resp = await client.chat.completions.create(
                    model=settings.llm_model,
                    temperature=0.5,
                    max_tokens=120,
                    messages=[{
                        "role": "user",
                        "content": (
                            f"用户有这些衣物：{names}。场合：{occasion}。"
                            f"用 1-2 句话给出搭配建议和理由（只谈搭配本身，不要提及数据库/工具）。"
                        ),
                    }],
                )
                tip = (tip_resp.choices[0].message.content or "").strip()
                if tip:
                    lines.append(f"AI 提示：{tip}")
        except Exception as e:
            logger.warning("outfit_daily 润色失败（降级为规则结果）: %s", e)
        lines.append("（搭配依据：你的真实衣物 + 场合规则，非凭空推荐）")
        return "\n".join(lines)
    except PermissionError:
        raise
    except Exception as e:
        logger.warning("outfit_daily 调用失败: %s", e)
        return "穿搭建议暂时给不了，稍后再试试？"





# ── 购物后 AI 服务 ──

AFTERSALE_STATUS_HINT = {
    "PENDING": "待审核（商家将在 24 小时内处理）",
    "APPROVED": "已通过（等待退款/换货执行）",
    "REFUNDING": "退款处理中",
    "REFUNDED": "已退款（款项将按原路退回）",
    "REJECTED": "已驳回",
    "CANCELLED": "已取消",
    # 真实状态枚举（AftersaleState，与 aftersale-service 对齐）
    "APPLYING": "待审核（商家将在 24 小时内处理）",
    "SELLER_APPROVED": "已通过（等待退款/换货执行）",
    "CANCELED": "已取消",
}

AFTERSALE_TYPE_HINT = {
    "refund": "仅退款（未收到货或未使用）",
    "return_refund": "退货退款（已收到货，退货后退款）",
    "exchange": "换货（同款换新）",
    "repair": "维修（质保期内免费维修）",
}


@tool
async def aftersale_assistant(issue: str = "", aftersale_no: str = "") -> str:
    """
    AI 售后助手。处理两类场景：
    1) 用户描述商品问题（issue）→ 生成专业售后话术（问题描述、建议诉求、证据清单、适用售后类型）并提示发起售后；
    2) 用户提供售后单号（aftersale_no）→ 查询售后进度、解读当前状态并给出下一步行动建议。

    用户说"商品有问题""怎么申请售后""售后到哪一步了""我要退货/换货/退款"时调用。

    Args:
        issue: 商品问题描述（可选，如"耳机左耳没声音，用了三天"）
        aftersale_no: 售后单号（可选，查询进度用）
    """
    try:
        headers = {"x-user-id": _get_current_user()}
        if aftersale_no:
            url = f"{settings.aftersale_service_url}/aftersale/{aftersale_no}"
            data = await _http_get(url, headers=headers)
            o = data.get("data") or {}
            if not o:
                return f"未查到售后单 {aftersale_no}，请核对单号。"
            status = (o.get("status") or "UNKNOWN").upper()
            hint = AFTERSALE_STATUS_HINT.get(status, "状态未知")
            lines = [
                f"售后进度｜单号 {aftersale_no}",
                f"类型：{AFTERSALE_TYPE_HINT.get(o.get('type') or '', o.get('type') or '未知')}",
                f"状态：{status}（{hint}）",
                f"申请原因：{o.get('reason') or '未填写'}",
                f"申请金额：¥{(o.get('amount') or 0) / 100:.2f}",
                f"申请时间：{o.get('applyAt') or '—'}",
            ]
            if o.get("rejectReason"):
                lines.append(f"驳回原因：{o['rejectReason']}")
            if status in ("PENDING", "APPROVED"):
                lines.append("下一步：请耐心等待商家处理，通常 24 小时内会有结果；如需加急可联系在线客服。")
            elif status == "REJECTED":
                lines.append("下一步：若对驳回有异议，可重新发起售后并补充证据（照片/视频/物流凭证）。")
            elif status in ("REFUNDING", "REFUNDED"):
                lines.append(f"下一步：退款{'正在处理' if status == 'REFUNDING' else '已完成'}，款项退回原支付渠道，一般 1-7 个工作日到账。")
            return "\n".join(lines)

        # 生成售后话术
        issue = (issue or "").strip()
        if not issue:
            try:
                url = f"{settings.aftersale_service_url}/aftersale/mine?userId={_get_current_user()}"
                data = await _http_get(url, headers=headers)
                items = data.get("data") or []
                if items:
                    lines = ["你的售后单列表："]
                    for it in items[:5]:
                        lines.append(f"· {it.get('aftersaleNo')} | {AFTERSALE_TYPE_HINT.get(it.get('type') or '', it.get('type') or '')} | {it.get('status')} | 金额 ¥{(it.get('amount') or 0) / 100:.2f}")
                    lines.append("回复售后单号可查详情；或直接描述商品问题，我帮你生成售后话术。")
                    return "\n".join(lines)
                return "暂无售后记录。可描述你遇到的商品问题，我帮你生成专业的售后申请话术。"
            except Exception:
                return "暂无售后记录。可描述你遇到的商品问题，我帮你生成专业的售后申请话术。"

        # 诉求与类型推断（简单规则）
        req = ""
        if any(k in issue for k in ("没收到", "一直不发货", "未发货")):
            req = "要求尽快发货或退款"
            atype = "refund"
        elif any(k in issue for k in ("坏的", "坏了", "不响", "不能用", "故障", "质量问题")):
            req = "申请退款或换货，并附检测说明"
            atype = "exchange"
        elif any(k in issue for k in ("不喜欢", "不合适", "尺码", "色差", "想退")):
            req = "申请退货退款（不影响二次销售）"
            atype = "return_refund"
        else:
            req = "申请按平台售后政策处理（退款/换货/维修）"
            atype = "repair"
        lines = [
            f"专业售后话术（类型建议：{AFTERSALE_TYPE_HINT.get(atype, atype)}）",
            f"【问题描述】我在 {issue}。已核对商品与订单信息，问题属实。",
            f"【诉求】{req}。",
            "【证据建议】附上商品照片/短视频（问题部位特写）+ 订单截图 + 物流外包装照片（如涉及）；",
            "【时效】根据《消费者权益保护法》与平台七天无理由政策，售后申请应在受理后 24 小时内处理，退款 1-7 个工作日到账。",
            "【下一步】在订单详情页发起售后（选择对应类型），或回复确认后我帮你跳转/整理成申请草稿。",
        ]
        return "\n".join(lines)
    except Exception as e:
        logger.warning("aftersale_assistant 调用失败: %s", e)
        return "售后信息暂时获取失败，请稍后再试或联系人工客服。"


LOGISTICS_NODE_ORDER = {"已下单": 1, "已付款": 2, "已发货": 3, "运输中": 4, "派送中": 5, "已签收": 6}


@tool
async def logistics_tracker(shipment_no: str) -> str:
    """
    智能物流管家。查询物流轨迹并做时效评估：
    汇总最新节点与时间、判断是否停滞（长时间无更新）、给出延误预警与替代建议。

    用户问"物流到哪了""快递什么时候到""包裹卡住了/一直不动"时调用。

    Args:
        shipment_no: 运单号（shipmentNo）
    """
    try:
        url = f"{settings.logistics_service_url}/shipment/{shipment_no}/track"
        headers = {"x-user-id": _get_current_user()}
        data = await _http_get(url, headers=headers)
        events = data.get("data") or []
        if not events:
            return f"未查到运单 {shipment_no} 的物流轨迹，请核对运单号。"
        latest = events[-1]
        node = latest.get("nodeName") or ""
        desc = latest.get("description") or ""
        node_time = latest.get("nodeTime") or "—"
        lines = [
            f"物流轨迹｜运单 {shipment_no}",
            f"最新节点：{node}（{node_time}）{('：' + desc) if desc else ''}",
            "最近轨迹：",
        ]
        for ev in reversed(events[-4:]):
            lines.append(f"· {ev.get('nodeTime') or '—'} {ev.get('nodeName') or ''} {ev.get('description') or ''}")
        # 停滞预判：最新节点非签收且是"运输中/派送中"，提示留意
        if node and node not in ("已签收",):
            lines.append("时效提示：当前仍在运输/派送中，正常情况下 1-2 天内会有新进展。")
            lines.append("若超过 48 小时无新节点，建议：① 联系在线客服催件；② 申请物流客服介入；③ 必要时发起售后（未按时送达可依据承诺时效申请补偿）。")
        else:
            lines.append("已签收：请及时验货，如有破损/少件可在 48 小时内联系售后。")
        return "\n".join(lines)
    except Exception as e:
        logger.warning("logistics_tracker 调用失败: %s", e)
        return "物流信息暂时获取失败，请稍后再试。"



# ── 使用周期管理 ──

CYCLE_RULES = [
    # (关键词元组, 品类, 建议补货/换新周期文案, 提示)
    (("纸巾", "抽纸", "湿巾", "卷纸"), "纸品消耗品", "建议每 1-2 个月补货一次", "按家庭用量提前囤货更划算"),
    (("洗衣液", "洗洁精", "洗洁", "清洁", "垃圾袋"), "家清消耗品", "建议每 2-3 个月补货一次", "大容量装单价更低"),
    (("牙膏", "洗发水", "沐浴露", "洗发", "沐浴", "香皂"), "个护消耗品", "建议每 2-3 个月补货一次", "套装/多支装更省"),
    (("面膜", "护肤品", "精华", "面霜", "水乳"), "美妆护肤", "建议每 3-6 个月按需补货", "开封后注意保质期（通常 6-12 个月）"),
    (("咖啡", "茶叶", "零食", "饼干", "坚果", "矿泉水", "饮料"), "食品饮品", "建议按消耗频率 1 个月左右补货", "注意保质期，避免囤货过期"),
    (("耳机", "蓝牙", "键盘", "鼠标", "手表", "充电", "数据线"), "数码配件", "建议使用 2-3 年后考虑换新/升级", "出现续航下降/卡顿可提前以旧换新"),
    (("鞋", "运动鞋", "跑鞋", "T恤", "衬衫", "外套", "卫衣", "裤"), "服饰", "建议按季节/磨损每 1-2 年补充", "换季时关注折扣"),
    (("保温杯", "水杯", "电饭煲", "锅", "小家电"), "家居耐用品", "建议使用 2-3 年后检查是否需要更换", "功能正常无需过早更换"),
]


@tool
async def usage_cycle_assistant() -> str:
    """
    使用周期管理。基于用户已购订单（品类聚合）生成消耗品补货提醒、
    食品保质期提示、耐用品换新时机建议。

    用户问"我该补点什么了""上次买的什么时候用完""有没有快到期的""哪些该换新了"时调用。

    Args: 无（用户身份由服务端注入）
    """
    try:
        url = f"{settings.order_service_url}/order/mine?userId={_get_current_user()}"
        headers = {"x-user-id": _get_current_user()}
        data = await _http_get(url, headers=headers)
        orders = data.get("data") or []
        seen_items: dict[str, int] = {}
        for o in orders:
            if o.get("orderStatus") in ("CANCELLED", "CLOSED"):
                continue
            for it in o.get("items") or []:
                name = it.get("skuName") or ""
                if not name:
                    continue
                seen_items[name] = seen_items.get(name, 0) + (it.get("count") or 1)
        if not seen_items:
            return "你还没有已购订单。买过东西之后，我可以帮你做消耗品补货与换新提醒。"

        hits: dict[str, tuple] = {}
        for name, qty in seen_items.items():
            for keywords, cat, cycle, tip in CYCLE_RULES:
                if any(k in name for k in keywords):
                    hits.setdefault(name, (cat, cycle, tip, qty))
                    break
        if not hits:
            return "已购商品暂未匹配到周期管理品类（消耗品/食品/耐用品）。可以告诉我具体品类，我帮你估算。"
        lines = [
            "你的使用周期管理清单（基于已购订单）：",
        ]
        for name, (cat, cycle, tip, qty) in sorted(hits.items()):
            lines.append(f"· {name} ×{qty}｜{cat}")
            lines.append(f"   → {cycle}；{tip}")
        lines.append("提示：以上为通用估算，具体请结合实际消耗速度；食品/护肤品注意开封后保质期。")
        lines.append("需要我帮你把某类加入购物车补货，或对比同品类更划算的规格吗？")
        return "\n".join(lines)
    except Exception as e:
        logger.warning("usage_cycle_assistant 调用失败: %s", e)
        return "周期管理信息暂时获取失败，请稍后再试。"

# ── AI 送礼助手 ──

GIFT_PROFILE_RULES: dict[str, dict] = {
    "女朋友": {
        "keywords": ["香水", "口红", "首饰", "花束", "项链"],
        "reason": "浪漫与心意优先，选择高颜值、可表达爱意的品类",
        "note": "可搭配贺卡 + 鲜花，仪式感拉满",
    },
    "男朋友": {
        "keywords": ["蓝牙耳机", "运动鞋", "机械键盘", "游戏手柄", "手表"],
        "reason": "实用 + 兴趣向，选他日常用得上又显用心的品类",
        "note": "数码配件记得确认型号兼容",
    },
    "父母": {
        "keywords": ["按摩", "养生", "滋补", "保暖", "茶叶"],
        "reason": "健康与体贴优先，功能性强的实用品类最稳妥",
        "note": "偏实体店试用型商品建议先看评价",
    },
    "孩子": {
        "keywords": ["玩具", "绘本", "文具", "积木"],
        "reason": "寓教于乐，安全适龄是第一位",
        "note": "注意适用年龄与安全标准",
    },
    "朋友": {
        "keywords": ["香薰", "咖啡", "手账", "保温杯", "零食"],
        "reason": "轻松有趣不踩雷，价位适中表达情谊",
        "note": "预算内选最有趣或最有话题性的",
    },
    "师长": {
        "keywords": ["书", "钢笔", "保温杯", "茶叶"],
        "reason": "得体与实用，表达敬意不刻意",
        "note": "避免过于个人化的礼物",
    },
    "长辈": {
        "keywords": ["足浴盆", "按摩枕", "保温杯", "养生"],
        "reason": "健康关怀，适老实用是核心",
        "note": "操作要简单，优先大字/语音款",
    },
}

GIFT_OCCASION_CARDS = {
    "生日": "愿你的每一个愿望都能如期而至，生日快乐！",
    "纪念日": "纪念日快乐！愿岁岁年年，心意如初。",
    "情人节": "把心意藏在礼物里，愿你每一天都甜。",
    "七夕": "七夕快乐！星月可寄，心意可托。",
    "新年": "新年快乐！愿你岁岁常欢愉，万事皆胜意。",
    "圣诞": "圣诞快乐！愿你被温柔以待，平安喜乐。",
    "乔迁": "乔迁之喜！新居新气象，万事皆顺意。",
    "感谢": "谢谢你一直以来的照顾，一点心意请收下。",
    "道歉": "对不起，希望这份小小心意能让你心情好一点。",
    "通用": "一点心意，不成敬意，希望你喜欢！",
}


@tool
async def gift_assistant(recipient: str, occasion: str, budget: float = 0) -> str:
    """
    送礼方案助手。根据收礼人关系、场景与预算生成可落地的送礼方案：
    推断收礼人画像 → 搜索预算内真实在售商品 → 每件给出推荐理由 → 附贺卡文案与心意小贴士。

    用户表达送礼意图（"送XX什么礼物""生日礼物""纪念日送什么""过节送XX")时调用。

    Args:
        recipient: 收礼人关系，如"女朋友""男朋友""父母""孩子""闺蜜""老师""长辈"
        occasion: 场景，如"生日""纪念日""情人节""七夕""新年""圣诞""乔迁""感谢""道歉"
        budget: 预算（元），可选；不填由对话推断，0 表示不限制
    """
    rec = (recipient or "").strip()
    rel = "朋友"
    if any(k in rec for k in ("女朋友", "女友", "对象", "老婆", "妻子", "女生")):
        rel = "女朋友"
    elif any(k in rec for k in ("男朋友", "男友", "老公", "丈夫", "男生")):
        rel = "男朋友"
    elif any(k in rec for k in ("父母", "妈妈", "母亲", "爸爸", "父亲", "爸妈")):
        rel = "父母"
    elif any(k in rec for k in ("孩子", "女儿", "儿子", "宝宝", "小朋友")):
        rel = "孩子"
    elif any(k in rec for k in ("老师", "导师", "师长", "教授")):
        rel = "师长"
    elif any(k in rec for k in ("老人", "长辈", "爷爷", "奶奶", "外公", "外婆", "姥姥", "姥爷")):
        rel = "长辈"

    rule = GIFT_PROFILE_RULES[rel]
    budget_fen = int(budget * 100) if budget and budget > 0 else 0
    occ = (occasion or "通用").strip()

    cands: list[dict] = []
    try:
        headers = {"x-user-id": _get_current_user()}
        for kw in rule["keywords"]:
            try:
                url = f"{settings.product_service_url}/product/page?keyword={kw}&pageNum=1&pageSize=5"
                data = await _http_get(url, headers=headers)
                items = data.get("data", {}).get("records", []) or []
                for it in items:
                    skus = it.get("skus") or []
                    sku_id = skus[0].get("skuId") if skus else None
                    if not sku_id:
                        continue
                    cands.append({
                        "name": it.get("name") or "未知商品",
                        "spuId": it.get("spuId"),
                        "skuId": sku_id,
                        "priceFen": it.get("priceMin") or 0,
                        "kw": kw,
                    })
            except Exception:
                continue
    except Exception as e:
        logger.warning("gift_assistant 搜索失败: %s", e)
        return "送礼方案生成失败：暂时无法获取商品信息，请稍后再试。"

    if not cands:
        return "暂时没有搜到适合该收礼人的礼物商品，可以换个关系/场景再试。"

    seen: set[str] = set()
    picked: list[dict] = []
    over_budget = False
    for c in sorted(cands, key=lambda x: x["priceFen"]):
        if c["skuId"] in seen:
            continue
        seen.add(c["skuId"])
        if budget_fen and c["priceFen"] > budget_fen:
            continue
        picked.append(c)
        if len(picked) >= 3:
            break
    if budget_fen and not picked:
        over_budget = True
        for c in sorted(cands, key=lambda x: x["priceFen"]):
            if c["skuId"] in seen:
                continue
            seen.add(c["skuId"])
            picked.append(c)
            if len(picked) >= 3:
                break

    card = GIFT_OCCASION_CARDS.get(occ, GIFT_OCCASION_CARDS["通用"])
    lines = [
        f"送礼方案｜收礼人：{recipient}（画像推断：{rel}）｜场景：{occasion}",
        "候选礼物（均为本平台真实在售商品）：",
    ]
    for i, c in enumerate(picked, 1):
        price_txt = f"¥{c['priceFen'] / 100:.2f}"
        lines.append(f"{i}. {c['name']} | {price_txt}")
        lines.append(f"   推荐理由：{rule['reason']}；围绕「{c['kw']}」品类挑选，契合「{rel}」画像")
        lines.append(f"   可直接购买：skuId={c['skuId']}（加购或代下单均可）")
    lines.append(f"贺卡文案（{occ}）：「{card}」")
    lines.append(f"小贴士：{rule['note']}。预算：{'%d 元' % budget if budget else '未指定'}；")
    if over_budget:
        lines.append("当前候选均超出预算，已按最低价排序供参考，建议调低预算或选择更小规格。")
    return "\n".join(lines)



@tool
async def scheduled_buy(action: str = "list", sku_id: str | None = None, count: int = 1,
                        frequency: str = "weekly", run_at: str | None = None,
                        weekday: str | None = None, task_id: str | None = None,
                        confirm: bool = False) -> str:
    """
    Agent 定时购：创建 / 查看 / 取消「定时自动下单」任务。
    用户说"定时买""自动买""每周买""每天买""定期买""帮我定时下单"时调用；
    创建必须由用户明确确认（confirm=true），到点后自动复用下单链路创建订单（待用户支付）。
    注意：与"补货提醒/该买了"（usage_cycle_assistant）区分——本工具执行实际下单，后者只做使用周期提醒。

    Args:
        action: list（查看我的定时购，默认）/ create（创建）/ cancel（取消）
        sku_id: 商品 SKU ID（create 必填）
        count: 每次购买数量，默认 1
        frequency: once（一次性）/ daily（每天）/ weekly（每周，默认）
        run_at: 执行时刻 HH:MM，如 "10:00"；once 可传完整日期时间 "2026-10-25 10:00"
        weekday: weekly 时的星期，如 "周一"…"周日"（默认当天）
        task_id: cancel 时的任务 ID
        confirm: create 时必须为 true（用户确认购买意向与频率）
    """
    from app import scheduled_buy as sb
    try:
        uid = _get_current_user()
        if action == "create":
            if not sku_id:
                return "创建定时购需要商品 SKU ID（可先调用商品搜索确认商品后给出）。"
            if not run_at:
                return "创建定时购需要执行时刻 run_at（如 10:00；一次性可传 2026-10-25 10:00）。"
            if not confirm:
                freq_hint = {"once": "一次性", "daily": "每天", "weekly": "每周"}.get(frequency, frequency)
                when = f"{weekday} {run_at}" if frequency == "weekly" and weekday else run_at
                return (f"创建定时购（待你确认）：SKU {sku_id} x{count}｜{freq_hint} {when}"
                        f"｜到点后自动下单生成订单（待支付，不会自动扣款）。回复『确认』后生效。")
            task = sb.create_task(uid, sku_id, count, frequency, run_at, weekday)
            return (f"定时购已创建 ✅：{sb.format_task(task)}"
                    f"｜到点自动下单（复用一键代下单链路，生成订单后提醒你支付，不会自动扣款）。")
        if action == "cancel":
            if not task_id:
                return "取消定时购需要任务 ID（可先查看列表获取）。"
            t = sb.cancel_task(uid, task_id)
            if not t:
                return f"未找到任务 {task_id}（不属于你或不存在）。"
            return f"定时购已取消 ✅：{sb.format_task(t)}"
        # action=list
        tasks = sb.list_tasks(uid)
        if not tasks:
            return "你还没有定时购任务。可以说『每周五 10:00 自动买 XX』，我帮你创建（需你确认）。"
        lines = ["你的定时购任务："]
        for t in tasks:
            lines.append(sb.format_task(t))
        return "\n".join(lines)
    except Exception as e:
        logger.warning("scheduled_buy 调用失败: %s", e)
        return "定时购服务暂时不可用，请稍后再试。"

# ── 工具注册表 ──

@tool
async def groupbuy_finder(sku_id: str | None = None, keywords: str | None = None) -> str:
    """
    拼团搜索 / 开团推荐。查询当前进行中的拼团活动（拼团价 vs 单人价、成团人数、正在拼的团还差几人）。
    用户问"有没有拼团""拼团价多少""想找人一起拼""这个能拼团吗""开团/参团"时调用。

    Args:
        sku_id: 商品 SKU ID（可选，精确匹配某个商品）
        keywords: 品类/标题关键词（可选，模糊匹配活动标题）
    """
    try:
        headers = {"x-user-id": _get_current_user()}
        data = await _http_get(
            f"{settings.marketing_service_url}/group-buy/activities",
            headers=headers,
        )
        acts = data.get("data") or []
        if not acts:
            return "当前暂无进行中的拼团活动。"
        if sku_id:
            acts = [a for a in acts if str(a.get("skuId")) == str(sku_id)]
        if keywords:
            kw = keywords.strip()
            acts = [a for a in acts if kw.lower() in (a.get("title") or "").lower()]
        if not acts:
            return "没有找到匹配的拼团活动，可以看看其他商品或等平台上新。"
        lines = []
        for a in acts:
            save = a["soloPrice"] - a["groupPrice"]
            lines.append(
                f"· {a['title']}（skuId={a['skuId']}）\n"
                f"  单人价 ¥{a['soloPrice'] / 100:.2f} → 拼团价 ¥{a['groupPrice'] / 100:.2f}"
                f"（省 ¥{save / 100:.2f}），{a['groupSize']} 人成团"
            )
            opens = a.get("openGroups") or []
            if opens:
                for g in opens[:3]:
                    lines.append(
                        f"  - 可加入团#{g['groupId']}：已 {g['memberCount']}/{g['targetSize']} 人，"
                        f"还差 {g['remain']} 人成团"
                    )
            else:
                lines.append("  - 暂无在拼的团，可以直接开团拉好友")
        lines.append("提示：开团/参团后按拼团价支付；成团失败自动退回原价差额。")
        return "拼团情报（当前进行中）：\n" + "\n".join(lines)
    except Exception as e:
        logger.warning("groupbuy_finder 调用失败: %s", e)
        return "拼团信息暂时没查到，稍后再试试？"


# ── 商家端 AI 经营体系 · 一期：经营概览助手 ──

@tool
async def merchant_overview() -> str:
    """
    商家经营概览 / 销售报表。查询平台订单总数、累计销售额、今日订单与销售额、
    订单状态分布、热销商品 Top5、待处理售后数（单商家市场，商家视角 = 平台聚合，演示口径）。
    用户问"经营情况""销售报表""今天卖了多少""订单情况""热销商品"时调用。
    """
    try:
        data = await _http_get(
            f"{settings.order_service_url}/order/stats/overview",
        )
        d = data.get("data") or {}
        sales = d.get("totalSalesFen") or 0
        today_sales = d.get("todaySalesFen") or 0
        dist = d.get("statusDist") or {}
        dist_txt = ", ".join(f"{k} {v} 单" for k, v in dist.items()) or "暂无"
        lines = [
            "商家经营概览（平台聚合 · 演示口径）：",
            f"· 订单总数：{d.get('totalOrders', 0)} 单；今日新增 {d.get('todayOrders', 0)} 单",
            f"· 累计销售额：¥{sales / 100:.2f}；今日销售额：¥{today_sales / 100:.2f}",
            f"· 订单状态分布：{dist_txt}",
            f"· 待处理售后：{d.get('pendingAfterSale', 0)} 单",
        ]
        hot = d.get("hotSpus") or []
        if hot:
            lines.append("· 热销商品 Top5：")
            for i, h in enumerate(hot, 1):
                lines.append(
                    f"  {i}. {h.get('spuName') or h.get('spuId')} "
                    f"| 售出 {h.get('soldCount', 0)} 件 | ¥{h.get('salesFen', 0) / 100:.2f}"
                )
        else:
            lines.append("· 热销商品：暂无销售数据")
        lines.append("提示：演示环境为单商家市场，商家视角取平台聚合数据；正式多商家需按 merchantId 隔离。")
        return "\n".join(lines)
    except Exception as e:
        logger.warning("merchant_overview 调用失败: %s", e)
        return "经营数据暂时没查到，稍后再试试？"


# ── 商家端 AI 经营体系 · 二期：经营预警助手 ──

@tool
async def merchant_warnings(threshold: int = 10) -> str:
    """
    商家经营预警：低库存 SKU 列表 + 近期低分差评（负面预警）。
    用户问"有什么需要处理的""库存预警""缺货提醒""差评预警""负面评价"时调用。
    threshold 为低库存阈值（余量小于等于该值告警），默认 10。
    """
    try:
        low = await _http_get(
            f"{settings.inventory_service_url}/inventory/low-stock?threshold={threshold}",
        )
        neg = await _http_get(
            f"{settings.product_service_url}/product/review/negative?minRating=3&limit=5",
            headers={"X-User-Id": "2107757313435291648"},
        )
        low_list = low.get("data") or []
        neg_list = neg.get("data") or []
        lines = ["商家经营预警（单商家市场 · 演示口径）："]
        if low_list:
            lines.append(f"· 低库存 SKU（余量 ≤ {threshold}）：")
            for s in low_list:
                lines.append(f"  - skuId={s.get('skuId')} 剩余 {s.get('available')} 件（库存 0 需补货）")
        else:
            lines.append(f"· 低库存 SKU（余量 ≤ {threshold}）：无，库存健康")
        if neg_list:
            lines.append("· 近期差评（rating ≤ 3）：")
            for r in neg_list:
                content = (r.get("content") or "").replace("\n", " ")[:40]
                lines.append(f"  - spuId={r.get('spuId')} {r.get('rating')} 星：{content}")
            lines.append("建议：优先回复差评解释原因，并对高频问题（质量/尺码/面料）做商品页改进。")
        else:
            lines.append("· 近期差评：无，评价口碑良好")
        lines.append("提示：库存为演示阈值口径；正式多商家需按 merchantId 隔离并接入推送触达。")
        return "\n".join(lines)
    except Exception as e:
        logger.warning("merchant_warnings 调用失败: %s", e)
        return "经营预警数据暂时没查到，稍后再试试？"


@tool
async def fulfillment_alert() -> str:
    """
    履约异常预警：把「待发货（PAID）订单」与「低库存 SKU」匹配，找出可能缺货卡单的订单。
    用户问"哪些订单要缺货""履约异常""发货会卡单吗""缺货订单""待发货但没库存"时调用。
    输出缺货 SKU、受影响的待发货订单清单与处理建议（补货/拆分/延期发货）。
    """
    try:
        pending = await _http_get(
            f"{settings.order_service_url}/order/stats/pending-fulfillment",
        )
        low = await _http_get(
            f"{settings.inventory_service_url}/inventory/low-stock?threshold=10",
        )
        pending_list = pending.get("data") or []
        low_list = low.get("data") or []
        stock = {int(s.get("skuId")): int(s.get("available") or 0) for s in low_list}

        lines = ["履约异常预警（单商家市场 · 演示口径）："]
        if not pending_list:
            lines.append("· 待发货订单：无（当前没有 PAID 待发货订单）")
            lines.append("· 低库存 SKU：{} 个".format(len(low_list)))
            lines.append("提示：正常履约通道；库存低于阈值可关注补货。")
            return "\n".join(lines)

        lines.append(f"· 待发货订单：{len(pending_list)} 单（PAID 状态）")
        at_risk = []
        for o in pending_list:
            for it in (o.get("items") or []):
                sku_id = int(it.get("skuId"))
                need = int(it.get("count") or 0)
                av = stock.get(sku_id)
                if av is not None and av < need:
                    at_risk.append({
                        "orderId": o.get("orderId"),
                        "skuId": sku_id,
                        "skuName": it.get("skuName") or f"SKU-{sku_id}",
                        "need": need,
                        "stock": av,
                    })
        if not at_risk:
            lines.append("· 缺货风险：无（待发货订单所需 SKU 库存均充足）")
        else:
            lines.append("· 缺货风险卡单（库存 < 需求）：")
            for r in at_risk:
                lines.append(
                    f"  - 订单 {r['orderId']}：{r['skuName']}（skuId={r['skuId']}）"
                    f"需 {r['need']} 件，库存仅 {r['stock']} 件，缺口 {r['need'] - r['stock']} 件"
                )
            lines.append(
                "· 处理建议：① 优先补货（低于安全库存下单给供应商）；"
                "② 拆分发货（有货 SKU 先发，缺货 SKU 补货后追发）；"
                "③ 联系买家说明延期并协商发货时间；"
                "④ 若缺口持续，考虑下架该 SKU 暂停新单。"
            )
        lines.append("提示：库存为演示阈值口径（≤10 件告警）；正式版需接入库存变动实时推送与自动补货任务。")
        return "\n".join(lines)
    except Exception as e:
        logger.warning("fulfillment_alert 调用失败: %s", e)
        return "履约预警数据暂时没查到，稍后再试试？"


@tool
async def supply_replenish(
    threshold: int = 10,
    target_qty: int = 50,
    do_replenish: bool = False,
) -> str:
    """
    供应链补货助手：低库存 SKU 盘点 + 最近补货记录查询；do_replenish=True 时执行自动补货（演示口径，低库存补到 target_qty）。
    用户问"补货""自动补货""低库存怎么处理""库存不足""补货记录""下单补货"时调用。
    threshold 为低库存阈值（默认 10），target_qty 为补货目标库存（默认 50）。
    仅演示触发真实补货动作；正式版应改为生成补货单并走供应商审批流程。
    """
    try:
        low = await _http_get(
            f"{settings.inventory_service_url}/inventory/low-stock?threshold={threshold}",
        )
        recs = await _http_get(
            f"{settings.inventory_service_url}/inventory/replenish/records?limit=5",
        )
        low_list = low.get("data") or []
        rec_list = recs.get("data") or []
        lines = ["供应链补货（单商家市场 · 演示口径）："]
        if low_list:
            lines.append(f"· 低库存 SKU（余量 ≤ {threshold}）：")
            for s in low_list:
                lines.append(
                    f"  - skuId={s.get('skuId')} 剩余 {s.get('available')} 件"
                    f"（低于安全库存，建议补货至 {target_qty} 件）"
                )
        else:
            lines.append(f"· 低库存 SKU（余量 ≤ {threshold}）：无，库存健康")

        if do_replenish:
            body = {"threshold": threshold, "targetQty": target_qty}
            resp = await _http_post(
                f"{settings.inventory_service_url}/inventory/auto-replenish",
                json_data=body,
            )
            done = resp.get("data") or []
            if done:
                lines.append(f"· 自动补货执行：{len(done)} 个 SKU 已补货")
                for r in done:
                    lines.append(
                        f"  - skuId={r.get('skuId')} {r.get('beforeQty')} → "
                        f"{r.get('afterQty')}（+{r.get('addQty')}，{r.get('triggerType')}）"
                    )
            else:
                lines.append("· 自动补货执行：无低库存 SKU，无需补货")
            lines.append("说明：演示直接改库存；正式版应生成补货单并接入供应商审批与采购回执。")
        else:
            lines.append("· 最近补货记录（5 条）：")
            if rec_list:
                for r in rec_list:
                    lines.append(
                        f"  - skuId={r.get('skuId')} {r.get('beforeQty')} → "
                        f"{r.get('afterQty')}（+{r.get('addQty')}，{r.get('triggerType')}）"
                        f" {r.get('createTime') or ''}"
                    )
            else:
                lines.append("  （暂无补货记录）")
            lines.append("如需自动补货请明确告知（将低库存 SKU 补至目标库存）。")
        lines.append("提示：库存为演示阈值口径；正式版接入库存实时变动推送、补货单审批与供应商采购回执。")
        return "\n".join(lines)
    except Exception as e:
        logger.warning("supply_replenish 调用失败: %s", e)
        return "补货数据暂时没查到，稍后再试试？"


@tool
async def fulfillment_action(
    action: str,
    order_ids: list[int] | None = None,
    reason: str | None = None,
) -> str:
    """
    履约异常订单自动处理：对待发货（PAID）订单执行处理动作并写处理记录。
    action 支持：SPLIT 拆分发货（有货先发，缺货追发）/ DELAY 延期发货 / OFF_SHELF 下架停单 / REPLENISH 补货后发货。
    order_ids 指定订单 ID 列表；不传则自动处理全部待发货订单（演示口径）。
    用户说"处理缺货订单""自动处理异常订单""拆分发货""延期发货""下架停单""补货后发货"时调用。
    """
    try:
        if action not in ("SPLIT", "DELAY", "OFF_SHELF", "REPLENISH"):
            return "支持的处理动作：SPLIT（拆分发货）/ DELAY（延期发货）/ OFF_SHELF（下架停单）/ REPLENISH（补货后发货）。请重新指定。"
        if not order_ids:
            pending = await _http_get(
                f"{settings.order_service_url}/order/stats/pending-fulfillment",
            )
            pending_list = pending.get("data") or []
            if not pending_list:
                return "当前没有待发货（PAID）订单，无需处理。"
            order_ids = [int(o.get("orderId")) for o in pending_list]
        body = {"action": action, "orderIds": order_ids, "reason": reason}
        resp = await _http_post(
            f"{settings.order_service_url}/order/fulfillment/action",
            json_data=body,
        )
        done = resp.get("data") or 0
        label = {"SPLIT": "拆分发货（有货先发、缺货追发）",
                 "DELAY": "延期发货（已与买家协商延期）",
                 "OFF_SHELF": "下架停单（暂停该 SKU 新单）",
                 "REPLENISH": "补货后发货（补到安全库存后履约）"}.get(action, action)
        lines = ["履约异常订单自动处理（单商家市场 · 演示口径）："]
        lines.append(f"· 处理动作：{label}")
        lines.append(f"· 处理订单：{len(order_ids)} 单（{done} 单成功写入处理记录）")
        if done > 0:
            lines.append("· 处理记录：已标记 DONE，商家中心可查；后续可继续补货/发货/通知买家。")
        lines.append("说明：演示直接写处理标记；正式版需接入审批流、物流调度与买家通知，并联动补货单。")
        return "\n".join(lines)
    except Exception as e:
        logger.warning("fulfillment_action 调用失败: %s", e)
        return "异常订单处理暂时没执行成功，稍后再试试？"


@tool
async def logistics_delay_alert(
    stagnant_hours: int = 48,
    dispatch_action: str | None = None,
    shipment_no: str | None = None,
    reason: str | None = None,
) -> str:
    """
    物流延误预警管家：扫描在途运单的疑似停滞/延误风险，并可一键调度处理。
    未传 dispatch_action 时只做预警盘点；传了 dispatch_action（URGE 催件 / REDELIVER 重新派送 / SELF_PICKUP 自提 / CHANGE_ADDRESS 改址 / RETURN 退换货）时对指定 shipment_no（不传则对第一个风险单）执行调度。
    用户问"快递卡住了吗""物流有没有延误""帮我催件/改自提/改地址"时调用。
    """
    try:
        alerts = await _http_get(
            f"{settings.logistics_service_url}/logistics/delay-alerts?stagnantHours={stagnant_hours}",
        )
        list_data = alerts.get("data") or []
        risk = [a for a in list_data if a.get("stagnant")]
        lines = ["物流延误预警（单商家市场 · 演示口径）："]
        if not list_data:
            lines.append("· 当前没有在途运单，无需预警。")
            return "\n".join(lines)
        lines.append(f"· 在途运单：{len(list_data)} 单；疑似停滞/延误：{len(risk)} 单")
        for a in risk[:5]:
            t = (a.get("latestTime") or "")[:16]
            lines.append(f"  - {a.get('shipmentNo')}（{a.get('latestNode') or '无轨迹'} @ {t}）：{a.get('hint')}")
        if risk and dispatch_action:
            target = shipment_no or risk[0].get("shipmentNo")
            d = await _http_post(
                f"{settings.logistics_service_url}/logistics/dispatch",
                json_data={"shipmentNo": target, "action": dispatch_action, "reason": reason},
            )
            rec = d.get("data") or {}
            label = {"URGE": "催件", "REDELIVER": "重新派送", "SELF_PICKUP": "改自提",
                     "CHANGE_ADDRESS": "改址", "RETURN": "退换货"}.get(dispatch_action, dispatch_action)
            lines.append(f"· 一键调度：运单 {target} 已执行「{label}」（记录 {rec.get('status') or 'DONE'}，商家/物流侧可查）")
            lines.append("说明：演示直接写调度记录；正式版需接入承运商 API、物流工单与买家通知。")
        elif risk and not dispatch_action:
            lines.append("· 建议：对风险单执行催件/重新派送/改自提/改址/退换货，告诉我即可一键处理。")
        return "\n".join(lines)
    except Exception as e:
        logger.warning("logistics_delay_alert 调用失败: %s", e)
        return "物流预警暂时获取失败，请稍后再试。"


@tool
async def after_sale_repair(
    action: str,
    order_no: str | None = None,
    sku_id: str | None = None,
    product_name: str | None = None,
    fault_desc: str | None = None,
    contact_phone: str | None = None,
    appointment_time: str | None = None,
) -> str:
    """
    售后助手：质保提醒与维修预约。
    action=warranty 时盘点我的商品质保状态（NORMAL 正常 / EXPIRING_SOON 30 天内到期 / EXPIRED 已过期），给出建议；action=repair 时创建维修预约单（需 order_no 订单号、fault_desc 故障描述、appointment_time 期望维修时间，如 2026-10-12T10:00:00）。
    用户问"我买的商品还在质保期吗""质保快到期了帮我提醒""东西坏了怎么维修/帮我约维修"时调用。
    """
    try:
        uid = _get_current_user()
        if action == "warranty":
            alerts = await _http_get(
                f"{settings.aftersale_service_url}/aftersale/warranty/alerts?userId={uid}&days=30",
            )
            items = alerts.get("data") or []
            if not items:
                return "当前没有质保记录。"
            lines = ["商品质保提醒（演示底座 · 正式版以订单/商品质保政策为准）："]
            risk = [a for a in items if a.get("status") in ("EXPIRING_SOON", "EXPIRED")]
            for a in items:
                label = {"NORMAL": "正常", "EXPIRING_SOON": "即将到期", "EXPIRED": "已过期"}.get(a.get("status"), a.get("status"))
                lines.append(f"· {a.get('productName')}（{label}，质保到期 {str(a.get('expireTime'))[:10]}，剩 {a.get('daysLeft')} 天）：{a.get('hint')}")
            if risk:
                lines.append("建议：临近/已过期商品如有问题，可告知我商品信息帮你约维修或生成售后话术。")
            else:
                lines.append("均在质保期内，放心使用。")
            return "\n".join(lines)
        if action == "repair":
            if not order_no or not fault_desc or not appointment_time:
                return "维修预约缺少必要信息：请提供订单号 order_no、故障描述 fault_desc、期望维修时间 appointment_time。"
            d = await _http_post(
                f"{settings.aftersale_service_url}/aftersale/repair/appointment?userId={uid}",
                json_data={"orderNo": order_no, "skuId": sku_id, "productName": product_name,
                           "faultDesc": fault_desc, "contactPhone": contact_phone,
                           "appointmentTime": appointment_time},
            )
            r = d.get("data") or {}
            if not r.get("appointmentNo"):
                return "维修预约提交失败：" + (d.get("message") or "未知原因") + "（正式版需商家确认排期与到店/寄修方式）"
            return (f"维修预约已提交 ✅：预约单号 {r.get('appointmentNo')}，状态 {r.get('status')}（待商家确认），"
                    f"期望时间 {str(r.get('appointmentTime'))[:16]}。商家确认后会有排期反馈；正式版支持在线改期与取消。")
        return "未知操作：action 仅支持 warranty（质保提醒）/ repair（维修预约）。"
    except Exception as e:
        logger.warning("after_sale_repair 调用失败: %s", e)
        return "售后助手暂时不可用，请稍后再试。"


@tool
async def after_sale_manager(
    action: str = "overview",
    issue: str = "",
    order_no: str = "",
    type: str = "",
    reason: str = "",
    confirm: bool = False,
) -> str:
    """
    AI 售后管家。
    action=overview（默认）：聚合诊断售后健康度——处理中售后单 + 物流延误预警 + 质保临近/过期，给出下一步建议；
    action=apply：一键发起售后——必须先给出订单号 order_no 与售后类型 type（refund 仅退款 / return_refund 退货退款 / exchange 换货 / repair 维修），
    且仅当用户明确确认（confirm=true）后才真正提交；未确认时只返回申请摘要请用户确认，绝不擅自执行（错误不骚扰红线）。

    用户问"我的售后情况""帮我处理售后""我要退款/退货/换货/申请售后""物流卡住/快递没到/一直没到""质保快到期"时调用。
    """
    try:
        uid = _get_current_user()
        if action == "apply":
            if not order_no or not type:
                return "发起售后需要：订单号 order_no 与售后类型 type（refund 仅退款 / return_refund 退货退款 / exchange 换货 / repair 维修）。"
            if not confirm:
                return (f"发起售后申请单（待你确认）：订单 {order_no}｜类型 {AFTERSALE_TYPE_HINT.get(type, type)}"
                        f"｜原因 {reason or issue or '未填写'}。回复『确认申请』后我再提交，不会擅自执行。")
            # 防编造金额：取 order-service 真实订单金额，不信任模型自报
            order = await _http_get(f"{settings.order_service_url}/order/{order_no}", headers={"x-user-id": uid})
            o = order.get("data") or {}
            amount = o.get("payAmount") or o.get("amount")
            if not amount:
                return f"未查到订单 {order_no} 的金额，无法发起售后；请核对订单号。"
            d = await _http_post(
                f"{settings.aftersale_service_url}/aftersale/apply",
                json_data={"orderNo": order_no, "type": type, "reason": reason or issue, "amount": amount},
                headers={"x-user-id": uid},
            )
            r = d.get("data") or {}
            if not r.get("aftersaleNo"):
                return "售后申请提交失败：" + (d.get("message") or "未知原因")
            return (f"售后申请已提交 ✅：单号 {r.get('aftersaleNo')}｜类型 {AFTERSALE_TYPE_HINT.get(type, type)}"
                    f"｜金额 ¥{amount / 100:.2f}｜状态 {r.get('status')}（待商家审核，24 小时内处理）。")
        # action=overview：三路聚合诊断（写/读均带内网透传头，服务端 JWT 过滤器依赖 X-User-Id）
        headers = {"x-user-id": uid}
        aftersales = await _http_get(f"{settings.aftersale_service_url}/aftersale/mine?userId={uid}", headers=headers)
        delays = await _http_get(f"{settings.logistics_service_url}/logistics/delay-alerts?userId={uid}", headers=headers)
        warranties = await _http_get(f"{settings.aftersale_service_url}/aftersale/warranty/alerts?userId={uid}&days=30", headers=headers)
        items = aftersales.get("data") or []
        d_items = delays.get("data") or []
        w_items = warranties.get("data") or []
        if not items and not d_items and not w_items:
            return "售后健康度诊断：暂无售后单、物流延误或质保风险 ✅。有商品问题可描述，我帮你生成售后话术或发起售后。"
        processing = [x for x in items if x.get("status") in ("APPLYING", "SELLER_APPROVED", "REFUNDING", "PENDING", "APPROVED")]
        risk_w = [x for x in w_items if x.get("status") in ("EXPIRING_SOON", "EXPIRED")]
        lines = ["售后健康度诊断："]
        if processing:
            lines.append("· 处理中售后 " + str(len(processing)) + " 单：" +
                         "；".join(f"{x.get('aftersaleNo')}（{AFTERSALE_TYPE_HINT.get(x.get('type') or '', x.get('type') or '')}）{x.get('status')}" for x in processing[:3]))
        else:
            lines.append("· 暂无处理中的售后单 ✅")
        if d_items:
            lines.append("· 物流延误预警 " + str(len(d_items)) + " 单：" +
                         "；".join(f"{x.get('shipmentNo')}（{x.get('reason') or '运输停滞'}）" for x in d_items[:3]))
        else:
            lines.append("· 暂无物流延误 ✅")
        if risk_w:
            lines.append("· 质保临近/过期 " + str(len(risk_w)) + " 件：" +
                         "；".join(f"{x.get('productName')}（剩 {x.get('daysLeft')} 天）" for x in risk_w[:3]))
        else:
            lines.append("· 质保均在期内 ✅")
        lines.append("下一步：回复售后单号查详情、『帮我处理售后』一键发起、或『物流管家/质保』分别查看。")
        return "\n".join(lines)
    except Exception as e:
        logger.warning("after_sale_manager 调用失败: %s", e)
        return "售后管家暂时不可用，请稍后再试。"




@tool
async def sales_forecast(days: int = 7, selection: bool = False) -> str:
    """商家经营大脑：7/30 天销量预测指导备货 + 智能选品（趋势/库存/热度）。days 预测天数（7 或 30）；selection=True 时输出智能选品建议清单。用于商家问销量趋势、该备多少货、哪些商品值得重点经营。"""
    url = settings.product_service_url + "/product/merchant/forecast?days=" + str(days)
    try:
        resp = await _http_get(url)
    except Exception as e:
        return f"经营大脑暂时不可用，请稍后再试（{e.__class__.__name__}）"
    rows = (resp or {}).get("data") or []
    if not rows:
        return "暂无销量预测数据（演示底座 daily_sales 尚未初始化）"
    if selection:
        sel_url = settings.product_service_url + "/product/merchant/selection"
        try:
            sel = await _http_get(sel_url)
        except Exception as e:
            return f"智能选品暂时不可用，请稍后再试（{e.__class__.__name__}）"
        sl = (sel or {}).get("data") or []
        lines = [f"【智能选品建议】共 {len(sl)} 项"]
        for x in sl:
            lines.append(
                f"- {x.get('productName')}（SKU {x.get('skuId')}）库存 {x.get('currentStock')}，"
                f"近 7 日销量 {x.get('last7Total')}，趋势 {x.get('trendPct')}%，"
                f"{x.get('hotLevel')}；建议：{x.get('reason')}"
            )
        return "\n".join(lines)
    out = [f"【{days} 天销量预测 · 演示口径（近 7 日日均 × 天数 × (1+趋势)，正式版接入时序模型）】"]
    for x in rows:
        out.append(
            f"- {x.get('productName')}（SKU {x.get('skuId')}）库存 {x.get('currentStock')}，"
            f"近 7 日 {x.get('last7Total')} 件、日均 {x.get('avgDaily')}、趋势 {x.get('trendPct')}%"
            f"（{x.get('hotLevel')}）；预测未来 {x.get('forecastDays')} 天销量约 {x.get('forecastQty')} 件，"
            f"建议备货补足 {x.get('suggestStock')} 件"
        )
    return "\n".join(out)



@tool
async def dynamic_pricing() -> str:
    """商家动态定价：基于销量趋势/库存水位/竞品均价给出每个 SKU 的建议价与调价动作（UP/DOWN/HOLD）。用于商家问"该涨价还是降价""定价建议""价格调整"。"""
    url = settings.product_service_url + "/product/merchant/pricing"
    try:
        resp = await _http_get(url)
    except Exception as e:
        return f"动态定价暂时不可用，请稍后再试（{e.__class__.__name__}）"
    rows = (resp or {}).get("data") or []
    if not rows:
        return "暂无定价建议数据（演示底座 daily_sales 尚未初始化）"
    out = ["【动态定价建议 · 演示口径（正式版接入价格弹性/竞品/促销日历）】"]
    for x in rows:
        act = x.get("action")
        flag = {"UP": "▲ 提价", "DOWN": "▼ 降价", "HOLD": "＝ 维持"}.get(act, act)
        out.append(
            f"- {x.get('productName')}（SKU {x.get('skuId')}）：现价 ¥{(x.get('currentPriceFen') or 0)/100}，"
            f"竞品均价 ¥{(x.get('competitorAvgFen') or 0)/100}，库存 {x.get('inventoryLevel')}，"
            f"趋势 {x.get('trendPct')}% → {flag}至 ¥{(x.get('suggestPriceFen') or 0)/100}。{x.get('reason')}"
        )
    return "\n".join(out)


@tool
async def marketing_plan() -> str:
    """商家营销方案：基于 SKU 热度/库存/趋势生成促销策略（会员价/拼团/满减等）与触达渠道建议。用于商家问"怎么做活动""营销方案""促销建议""怎么推这款商品"。"""
    url = settings.marketing_service_url + "/marketing/plan"
    try:
        resp = await _http_get(url)
    except Exception as e:
        return f"营销方案暂时不可用，请稍后再试（{e.__class__.__name__}）"
    rows = (resp or {}).get("data") or []
    if not rows:
        return "暂无营销方案建议"
    out = ["【营销方案建议 · 演示口径（正式版聚合销量/库存/券/拼团数据）】"]
    for x in rows:
        out.append(
            f"- {x.get('productName')}：策略【{x.get('strategy')}】{x.get('detail')}。"
            f"理由：{x.get('reason')}。触达渠道：{'、'.join(x.get('channels') or [])}"
        )
    return "\n".join(out)



@tool
async def review_assistant(action: str = "pending", review_id: str = "", content: str = "") -> str:
    """商家评论助手：待回复评论盘点（情感 + AI 建议话术 + 负面预警）/ 提交回复。action=pending 查待回复列表；action=reply 时传 review_id + content 提交商家回复。用于商家问"有哪些差评要处理""评论怎么回""负面评论预警""回复评论"。"""
    try:
        if action == "reply":
            if not review_id or not content:
                return "提交回复需要 review_id 与 content。"
            await _http_post(
                settings.product_service_url + "/product/review/merchant/reply",
                json_data={"reviewId": review_id, "content": content},
            )
            return "评论回复已提交 ✅（演示口径已写入 review_reply，正式版接入商家审核流）"
        resp = await _http_get(settings.product_service_url + "/product/review/merchant/pending?limit=10")
    except Exception as e:
        return "评论助手暂时不可用，请稍后再试（" + e.__class__.__name__ + "）"
    rows = (resp or {}).get("data") or []
    if not rows:
        return "暂无待回复评论 🎉"
    neg = [x for x in rows if x.get("sentiment") == "NEGATIVE"]
    lines = ["【待回复评论 %d 条 · 负面 %d 条 ⚠️】演示口径（正式版接情感模型+商家审核流）" % (len(rows), len(neg))]
    for x in rows:
        flag = {"NEGATIVE": "⚠️负面", "NEUTRAL": "· 中性", "POSITIVE": "✓ 正面"}.get(x.get("sentiment"), x.get("sentiment"))
        lines.append("- [%s] %s（%s 星）：%s" % (flag, x.get("userName"), x.get("rating"), x.get("content")))
        lines.append("  AI 建议回复：%s" % x.get("aiSuggestion"))
        lines.append('  回复请用：review_assistant(action="reply", review_id="%s", content="...")' % x.get("reviewId"))
    return "\n".join(lines)

@tool
async def price_alert(action: str, sku_id: str, target_price: Optional[str] = None) -> str:
    """
    降价关注与提醒。用户可关注某商品等待降价（可设期望价，不设则任意降价都提醒）、取消关注、
    查询自己关注的商品是否已降价/降价提醒。用户问"关注降价""降价提醒我""降到多少提醒我"
    "盯一下价格""降到 X 元提醒我""取消关注""我关注的降价了没"时调用。

    Args:
        action: "watch" 关注 / "unwatch" 取消关注 / "mine" 查看我的关注与提醒
        sku_id: 商品 SKU ID（watch/unwatch 必填，mine 可忽略）
        target_price: 期望价（元，watch 时可选；不填表示任意降价都提醒）
    """
    try:
        headers = {"x-user-id": _get_current_user()}
        if action == "mine":
            alerts_data = await _http_get(
                f"{settings.product_service_url}/price/alert/mine", headers=headers
            )
            notices_data = await _http_get(
                f"{settings.product_service_url}/price/notice/mine?unreadOnly=true", headers=headers
            )
            lines = ["【我的降价关注】"]
            alerts = alerts_data.get("data") or []
            if not alerts:
                lines.append("还没有关注任何商品。想让我帮你盯哪件商品降价？")
            for a in alerts:
                status = "已降 ¥%.2f" % (a.get("dropAmount", 0) / 100) if a.get("dropped") else "未降价"
                target = "（期望价 ¥%.2f）" % (a.get("targetPrice", 0) / 100) if a.get("targetPrice") else ""
                lines.append(f"· {a.get('skuName')}（SKU {a.get('skuId')}）当前 ¥{a.get('currentPrice', 0) / 100:.2f}，{status}{target}")
            notices = notices_data.get("data") or []
            if notices:
                lines.append(f"· 有 {len(notices)} 条未读降价提醒：")
                for n in notices[:3]:
                    lines.append(f"  - {n.get('skuName')} ¥{n.get('oldPrice', 0) / 100:.2f} → ¥{n.get('newPrice', 0) / 100:.2f}（降了 ¥{n.get('dropAmount', 0) / 100:.2f}）")
            return "\n".join(lines)

        if action == "watch":
            target_fen = None
            if target_price:
                try:
                    target_fen = int(round(float(target_price) * 100))
                except (TypeError, ValueError):
                    return f"期望价 {target_price} 格式不正确，请用元为单位。"
            body = {"skuId": sku_id}
            if target_fen is not None:
                body["targetPrice"] = target_fen
            data = await _http_post(
                f"{settings.product_service_url}/price/alert",
                body,
                headers=headers,
            )
            item = data.get("data") or {}
            return (f"已关注 {item.get('skuName', 'SKU-' + sku_id)} 的降价"
                    + (f"，降到 ¥{target_price} 提醒你" if target_fen is not None else "（任意降价都提醒你）")
                    + f"，当前价 ¥{item.get('currentPrice', 0) / 100:.2f}。")
        if action == "unwatch":
            await _http_get(
                f"{settings.product_service_url}/price/alert/{sku_id}",
                headers=headers,
                retry=False,
            )
            return f"已取消关注 SKU {sku_id} 的降价提醒。"
        return "不支持的 action：" + action
    except Exception as e:
        logger.warning("price_alert(%s) 调用失败: %s", action, e)
        return "降价提醒操作暂时没成功，稍后再试试？"


@tool
async def merchant_after_sale(status: str = "") -> str:
    """商家售后工作台：查看待处理售后列表与全状态统计，给出处理建议（只读，不擅自执行任何售后操作）。
    用于商家问「售后待办」「待审核的退款申请」「谁申请了退款/退货」「售后处理到哪一步」时调用。
    处理动作（同意退款/拒绝）请在商家中心人工确认后执行。"""
    try:
        uid = _get_current_user()
        url = f"{settings.aftersale_service_url}/aftersale/merchant/pending"
        if status:
            url += f"?status={status}"
        d = await _http_get(url, headers={"x-user-id": uid})
        data = d.get("data") or {}
        counts = data.get("counts") or {}
        items = data.get("list") or []
        lines = ["商家售后工作台："]
        if counts:
            lines.append(
                "· 在途统计：待审核 APPLYING "
                + str(counts.get("APPLYING", 0))
                + "｜待退款 SELLER_APPROVED "
                + str(counts.get("SELLER_APPROVED", 0))
                + "｜退款中 REFUNDING "
                + str(counts.get("REFUNDING", 0))
                + "｜已完结 REFUNDED "
                + str(counts.get("REFUNDED", 0))
            )
        if items:
            lines.append("· 当前列表（" + str(data.get("status") or "APPLYING") + "）" + str(len(items)) + " 单：")
            for x in items[:5]:
                lines.append(
                    "  - "
                    + str(x.get("aftersaleNo"))
                    + "｜"
                    + AFTERSALE_TYPE_HINT.get(x.get("type") or "", x.get("type") or "")
                    + "｜¥"
                    + f"{x.get('amount', 0) / 100:.2f}"
                    + "｜"
                    + str(x.get("status"))
                    + "｜"
                    + str(x.get("reason") or "无原因")
                )
        else:
            lines.append("· 当前筛选下无待处理单 ✅")
        lines.append("下一步：商家中心可查详情并人工处理（同意退款/拒绝并填写理由），AI 只读不擅自处理售后。")
        return "\n".join(lines)
    except Exception as e:
        logger.warning("merchant_after_sale 调用失败: %s", e)
        return "商家售后工作台暂时不可用，请稍后再试。"


@tool
async def customer_service(action: str = "faq", issue: str = "") -> str:
    """
    AI 在线客服。两类处理：
    1) action=faq（默认）：回答平台常见问题——运费规则 / 退货政策 / 发票 / 会员权益 / 发货时效 / 优惠券规则 / 支付方式；
       知识库关键词命中即返回确定答案，绝不编造；未命中如实说明，交由主对话继续；
    2) action=human：用户明确要求人工客服 / 投诉 / 举报 / 复杂问题升级——创建客服工单并返回工单号与预计响应。
       仅当用户明确表达「转人工 / 找人工客服 / 投诉 / 举报 / 人工处理」时才调用，绝不擅自建单（错误不骚扰红线）。

    用户问「运费怎么算」「退货政策」「发票怎么开」「会员有什么权益」「多久发货」「转人工」「投诉」「我要找客服」时调用。

    Args:
        action: 处理方式，faq=知识库答疑（默认），human=转人工创建工单
        issue: 用户的问题描述
    """
    try:
        from .customer_service import _classify, _match_faq, create_ticket, format_faq_answer, format_ticket

        text = issue or ""
        if action == "human":
            # 确认门复核：缺少强信号不建单（路由层已按强信号触发，这里双保险）
            _HUMAN_SIGNALS = ("人工", "转人工", "找客服", "投诉", "举报", "人工处理", "真人", "专员", "升级", "申诉")
            if not any(s in text for s in _HUMAN_SIGNALS):
                return "如需转人工客服，请明确说『转人工』或『找人工客服』；一般咨询我可以直接为您解答。"
            uid = _get_current_user()
            ticket = create_ticket(uid, _classify(text), text)
            return format_ticket(ticket)
        # action=faq 默认：知识库命中即返回确定答案
        hit = _match_faq(text)
        if hit:
            topic, lines = hit
            return format_faq_answer(topic, lines)
        return "（知识库未命中该问题，可进一步描述；或明确说『转人工』由人工客服跟进。）"
    except Exception as e:
        logger.warning("customer_service 调用失败: %s", e)
        return "客服服务暂时不可用，请稍后再试，或到『我的-帮助中心』反馈。"
@tool
async def reminders(action: str = "mine") -> str:
    """
    主动式智能提醒中心。聚合三类提醒：降价（关注商品降价通知）、补货（已购消耗品/食品/耐用品
    使用周期将尽）、换季穿搭（衣橱缺当季衣物）。默认返回本人未读提醒列表；也可手动触发一次扫描。

    用户问「有什么提醒」「提醒我」「该补货了吗」「换季了该买什么」「降价了提醒我」时调用。

    Args:
        action: mine=查看未读提醒（默认），scan=立即扫描一次生成新提醒
    """
    try:
        from .reminders import format_reminders, mine, run_once, touch_user
        uid = _get_current_user()
        touch_user(uid)
        if action == "scan":
            counts = await run_once(uid)
            summary = "，".join(f"{k} {v} 条" for k, v in counts.items() if v)
            fresh = mine(uid, unread_only=True)
            if summary:
                return f"扫描完成，新增提醒：{summary}\n\n" + format_reminders(fresh)
            return "本次扫描没有新的提醒。" + ("\n\n" + format_reminders(fresh) if fresh else "")
        items = mine(uid, unread_only=True)
        return format_reminders(items)
    except Exception as e:
        logger.warning("reminders 调用失败: %s", e)
        return "提醒中心暂时不可用，请稍后再试。"


TOOLS = [
    search_products,
    get_price,
    check_inventory,
    get_user_profile,
    apply_coupon,
    analyze_requirement,
    compare_prices,
    review_analysis,
    analyze_user_context,
    recommend_products,
    search_by_image,
    closet_scan_item,
    outfit_daily,
    gift_assistant,
    aftersale_assistant,
    logistics_tracker,
    logistics_delay_alert,
    after_sale_repair,
    usage_cycle_assistant,
    groupbuy_finder,
    optimize_cart,
    create_order,
    merchant_overview,
    merchant_warnings,
    fulfillment_alert,
    supply_replenish,
    fulfillment_action,
    sales_forecast,
    dynamic_pricing,
    marketing_plan,
    review_assistant,
    price_alert,
    merchant_after_sale,
    after_sale_manager,
    scheduled_buy,
    customer_service,
    reminders,
]
