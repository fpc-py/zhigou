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


async def _http_post(url: str, json_data: dict, timeout: float = 5.0, headers: dict | None = None) -> dict[str, Any]:
    """执行 HTTP POST 并返回 JSON body。"""
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
        data = await _http_get(url, timeout=5.0, headers=headers)
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
        data = await _http_get(f"{settings.product_service_url}/product/{spu_id}", timeout=5.0, headers=headers)
        spu = data.get("data") or {}
        if not spu:
            try:
                sku_data = await _http_get(
                    f"{settings.product_service_url}/product/sku/{spu_id}",
                    timeout=5.0,
                    headers=headers,
                )
                sku_info = sku_data.get("data") or {}
                real_spu = sku_info.get("spuId")
                if real_spu:
                    spu_id = str(real_spu)
                    data = await _http_get(f"{settings.product_service_url}/product/{spu_id}", timeout=5.0, headers=headers)
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
            timeout=5.0,
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
                    timeout=5.0,
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
    隐性需求挖掘。分析当前用户的历史订单，提炼购买偏好：常购品类、常用价位带、复购倾向。
    用户在表达需求时没说清偏好，或说"参考我买过的""我之前买过""跟上次差不多"时调用，
    用于补全隐性约束，让推荐更贴合用户历史习惯。用户身份由服务端注入，无需传参。
    """
    try:
        user_id = _get_current_user()
        url = f"{settings.order_service_url}/order/mine?userId={user_id}"
        headers = {"x-user-id": user_id}
        data = await _http_get(url, timeout=5.0, headers=headers)
        orders = data.get("data") or []
        if not orders:
            return "用户暂无历史订单，无法提炼购买偏好（推荐将按通用流程进行）。"

        # 品类统计（按 skuName 关键词归类）
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
        top_band = max(buckets, key=buckets.get) if buckets else None

        top_cats = sorted(category_count.items(), key=lambda kv: -kv[1])[:3]
        result = {
            "orderCount": len(orders),
            "totalItems": total_items,
            "topCategories": [{"category": cat, "count": n} for cat, n in top_cats],
            "priceBand": top_band,
            "hint": "以上来自用户历史订单统计，可作为隐性需求补全依据（默认品类/价位带/复购倾向）；无历史时不编造",
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
        cart_data = await _http_get(f"{settings.cart_service_url}/cart/mine", timeout=5.0, headers=cart_headers)
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
                        timeout=5.0,
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
            timeout=5.0,
            headers={"x-user-id": user_id},
        )
        base_data = base.get("data") or {}
        base_total = base_data.get("totalAmount") or 0
        base_discount = base_data.get("discountAmount") or 0

        # 4) 逐券试算，选最优
        coupons_data = await _http_get(
            f"{settings.marketing_service_url}/coupon/mine?userId={user_id}&status=UNUSED",
            timeout=5.0,
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
                    timeout=5.0,
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
        data = await _http_get(url, timeout=5.0, headers=headers)
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


async def _vision_extract(image_url: str) -> dict | None:
    """调 LLM 视觉模型提取商品特征（OpenAI 兼容 image_url）。60s 超时 + 一次重试，失败返回 None。"""
    from openai import AsyncOpenAI
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
                        {"type": "text", "text": "识别这张商品图，只输出 JSON：{'category': '品类', 'colors': ['颜色'], 'style': '风格', 'keywords': ['2-3个搜索关键词']}，不要解释。"},
                        {"type": "image_url", "image_url": {"url": image_url}},
                    ],
                }],
            )
            text = resp.choices[0].message.content or ""
            text = text.strip()
            if text.startswith("```"):
                text = text.strip("`").removeprefix("json").strip()
            parsed = json.loads(text)
            if parsed.get("keywords"):
                return parsed
        except Exception as e:
            last_err = e
            logger.warning("视觉识别第 %d 次失败 %s: %s", attempt, image_url, e)
    logger.warning("视觉识别最终失败 %s: %s", image_url, last_err)
    return None






# ── 购物后 AI 服务 ──

AFTERSALE_STATUS_HINT = {
    "PENDING": "待审核（商家将在 24 小时内处理）",
    "APPROVED": "已通过（等待退款/换货执行）",
    "REFUNDING": "退款处理中",
    "REFUNDED": "已退款（款项将按原路退回）",
    "REJECTED": "已驳回",
    "CANCELLED": "已取消",
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
            data = await _http_get(url, timeout=5.0, headers=headers)
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
                data = await _http_get(url, timeout=5.0, headers=headers)
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
        data = await _http_get(url, timeout=5.0, headers=headers)
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
        data = await _http_get(url, timeout=5.0, headers=headers)
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
                data = await _http_get(url, timeout=5.0, headers=headers)
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
            timeout=5.0,
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
    gift_assistant,
    aftersale_assistant,
    logistics_tracker,
    usage_cycle_assistant,
    groupbuy_finder,
    optimize_cart,
    create_order,
]