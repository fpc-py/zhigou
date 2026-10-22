"""
智购 AI Orchestrator — 主动式智能提醒（从被动问答到主动服务）

对齐《智购功能文档》「AI 原生购物助理」愿景：不只等用户问，主动在关键时刻提醒。

设计要点（对齐项目红线）：
- 不重复造轮子：三类提醒全部复用现成数据源，引擎只做「聚合 + 去重 + 状态机」：
  1. 降价提醒 → product-service `/price/notice/mine`（AI 省钱助手 B 块检测任务已产出降价通知）
  2. 复购提醒 → order-service `/order/mine` 品类聚合 + 复用 tools.CYCLE_RULES 周期规则
  3. 换季穿搭 → closet-service `/closet/items` 按当前季节覆盖检查
- 幂等去重：data/reminders.json 按 (userId, type, targetKey) 唯一，同一条不重复打扰；
- 状态机：ACTIVE(未读) → READ(已读) / DISMISSED(忽略)；
- 有界扫描：只扫「活跃用户」（AI 对话/工具调用时 touch_user 登记），默认零推送；
- 调度：每小时整点扫描（复用 scheduled_buy 的 60s 循环 + 小时变化检测），可手动触发；
- 持久化：data/reminders.json（tmp+replace 原子写，与会话/定时购同风格）。
"""

from __future__ import annotations

import asyncio
import json
import logging
import uuid
from datetime import datetime, timedelta
from pathlib import Path
from typing import Any

logger = logging.getLogger(__name__)

_DATA_DIR = Path(__file__).resolve().parent.parent / "data"
_REMINDERS_FILE = _DATA_DIR / "reminders.json"

_STATUS_ACTIVE = "ACTIVE"
_STATUS_READ = "READ"
_STATUS_DISMISSED = "DISMISSED"

# 换季规则：月份 → 季节（成都气候为典型四季）
_SEASONS = [
    (12, "冬"), (1, "冬"), (2, "冬"),
    (3, "春"), (4, "春"), (5, "春"),
    (6, "夏"), (7, "夏"), (8, "夏"),
    (9, "秋"), (10, "秋"), (11, "秋"),
]


def _current_season() -> str:
    return _SEASONS[datetime.now().month - 1][1]


def _load() -> dict[str, Any]:
    if not _REMINDERS_FILE.exists():
        return {"users": [], "reminders": []}
    try:
        data = json.loads(_REMINDERS_FILE.read_text(encoding="utf-8"))
        if isinstance(data, dict) and "reminders" in data:
            return data
        return {"users": [], "reminders": []}
    except Exception as e:
        logger.warning("reminders.json 读取失败: %s", e)
        return {"users": [], "reminders": []}


def _save(state: dict[str, Any]) -> None:
    _DATA_DIR.mkdir(parents=True, exist_ok=True)
    tmp = _REMINDERS_FILE.with_suffix(".json.tmp")
    tmp.write_text(json.dumps(state, ensure_ascii=False, indent=2), encoding="utf-8")
    tmp.replace(_REMINDERS_FILE)


def _uid() -> str:
    return "RM" + uuid.uuid4().hex[:12].upper()


def touch_user(user_id: str) -> None:
    """标记用户为活跃（AI 对话/工具调用时调用）：只扫活跃用户，默认零推送。"""
    state = _load()
    users = state["users"]
    if user_id not in users:
        users.append(user_id)
        _save(state)


def mine(user_id: str, unread_only: bool = False) -> list[dict[str, Any]]:
    """本人提醒列表（ACTIVE 优先，同批按时间倒序）。"""
    items = [r for r in _load()["reminders"] if r.get("userId") == user_id]
    if unread_only:
        items = [r for r in items if r.get("status") == _STATUS_ACTIVE]
    items.sort(key=lambda r: r.get("createdAt", ""), reverse=True)
    return items


def mark_read(user_id: str, reminder_id: str) -> dict[str, Any] | None:
    state = _load()
    for r in state["reminders"]:
        if r.get("id") == reminder_id and r.get("userId") == user_id:
            r["status"] = _STATUS_READ
            r["readAt"] = datetime.now().isoformat()
            _save(state)
            return r
    return None


def _add_reminder(state: dict[str, Any], user_id: str, rtype: str,
                  title: str, detail: str, target_key: str) -> bool:
    """幂等去重：同 (userId, type, targetKey) 已存在则不重复生成。返回是否新增。"""
    for r in state["reminders"]:
        if (r.get("userId") == user_id and r.get("type") == rtype
                and r.get("targetKey") == target_key and r.get("status") != _STATUS_DISMISSED):
            return False
    state["reminders"].append({
        "id": _uid(),
        "userId": user_id,
        "type": rtype,
        "title": title,
        "detail": detail,
        "targetKey": target_key,
        "status": _STATUS_ACTIVE,
        "createdAt": datetime.now().isoformat(),
        "readAt": None,
    })
    return True


async def _http_get(url: str, headers: dict[str, str] | None = None, timeout: float = 5.0) -> Any:
    import httpx
    async with httpx.AsyncClient(timeout=timeout) as client:
        r = await client.get(url, headers=headers or {})
        r.raise_for_status()
        return r.json()


async def _scan_price_drops(state: dict[str, Any], user_id: str) -> int:
    """降价提醒：消费省钱助手 B 块已产出的降价通知（unread）。"""
    from .config import settings
    try:
        url = f"{settings.product_service_url}/price/notice/mine?unreadOnly=true"
        data = await _http_get(url, headers={"x-user-id": user_id})
    except Exception as e:
        logger.warning("[reminders] price 源不可用 user=%s: %s", user_id, e)
        return 0
    n = 0
    for notice in data.get("data") or []:
        sku_id = notice.get("skuId")
        title = notice.get("title") or "降价提醒"
        detail = notice.get("content") or notice.get("message") or f"关注商品降价：{title}"
        key = f"sku:{sku_id}" if sku_id else f"notice:{notice.get('id')}"
        if _add_reminder(state, user_id, "price_drop", "降价提醒", detail, key):
            n += 1
    return n


async def _scan_repurchase(state: dict[str, Any], user_id: str) -> int:
    """复购提醒：已购订单品类聚合 → 周期规则命中 → 该补货/该换新。"""
    from .config import settings
    from .tools import CYCLE_RULES
    try:
        url = f"{settings.order_service_url}/order/mine?userId={user_id}"
        data = await _http_get(url, headers={"x-user-id": user_id})
    except Exception as e:
        logger.warning("[reminders] order 源不可用 user=%s: %s", user_id, e)
        return 0
    seen: dict[str, int] = {}
    for o in data.get("data") or []:
        if o.get("orderStatus") in ("CANCELLED", "CLOSED"):
            continue
        for it in o.get("items") or []:
            name = it.get("skuName") or ""
            if name:
                seen[name] = seen.get(name, 0) + (it.get("count") or 1)
    n = 0
    for name, qty in seen.items():
        for keywords, cat, cycle, tip in CYCLE_RULES:
            if any(k in name for k in keywords):
                if _add_reminder(
                    state, user_id, "repurchase",
                    f"{cat}补货提醒", f"已购 {name} ×{qty}｜{cycle}；{tip}", f"cat:{cat}"
                ):
                    n += 1
                break
    return n


async def _scan_season_outfit(state: dict[str, Any], user_id: str) -> int:
    """换季穿搭提醒：衣橱按当前季节覆盖检查（当季无衣物 → 提醒补充）。"""
    from .config import settings
    season = _current_season()
    try:
        url = f"{settings.closet_service_url}/closet/items?userId={user_id}&season={season}"
        data = await _http_get(url, headers={"x-user-id": user_id})
    except Exception as e:
        logger.warning("[reminders] closet 源不可用 user=%s: %s", user_id, e)
        return 0
    items = data.get("data") or []
    if items:
        return 0
    # 当季无衣物 → 提醒（不造轮子：调用衣橱穿搭推荐接口拿可读文案）
    detail = f"现在已是{season}季，你的衣橱还没有{season}季衣物。换季衣橱空空？看看应季新品补齐穿搭。"
    try:
        rec_url = f"{settings.closet_service_url}/closet/outfit/recommend?userId={user_id}"
        rec = await _http_get(rec_url, headers={"x-user-id": user_id}, timeout=3.0)
        if rec.get("data"):
            detail += "\n" + str(rec["data"])[:120]
    except Exception:
        pass
    return 1 if _add_reminder(state, user_id, "season_outfit",
                              f"换季穿搭提醒（{season}）", detail, f"season:{season}") else 0


async def run_once(user_id: str | None = None) -> dict[str, int]:
    """扫描一次提醒引擎。user_id 为空 → 扫全部活跃用户；指定用户 → 先登记为活跃再扫描。"""
    state = _load()
    if user_id:
        touch_user(user_id)
        state = _load()
    targets = [user_id] if user_id else state["users"]
    counts = {"price_drop": 0, "repurchase": 0, "season_outfit": 0}
    for uid in targets:
        if not uid:
            continue
        counts["price_drop"] += await _scan_price_drops(state, uid)
        counts["repurchase"] += await _scan_repurchase(state, uid)
        counts["season_outfit"] += await _scan_season_outfit(state, uid)
    if any(counts.values()):
        _save(state)
    return counts


def format_reminders(items: list[dict[str, Any]]) -> str:
    if not items:
        return "你当前没有待处理提醒。"
    type_names = {"price_drop": "降价", "repurchase": "补货", "season_outfit": "换季穿搭"}
    lines = ["你的提醒中心（未读优先）："]
    for r in items:
        mark = "●" if r.get("status") == _STATUS_ACTIVE else "○"
        lines.append(f"{mark} [{type_names.get(r.get('type'), r.get('type'))}] {r.get('title')}")
        lines.append(f"    {r.get('detail')}")
    return "\n".join(lines)


async def _scheduler_loop(interval_s: int = 60) -> None:
    """每小时整点扫描一次（60s 循环检测小时变化，复用 scheduled_buy 调度模式）。"""
    last_hour = None
    while True:
        try:
            now = datetime.now()
            if last_hour is None or now.hour != last_hour:
                last_hour = now.hour
                counts = await run_once()
                if any(counts.values()):
                    logger.info("提醒引擎扫描：%s", counts)
        except Exception as e:
            logger.warning("提醒引擎调度异常: %s", e)
        await asyncio.sleep(interval_s)


def start_scheduler() -> asyncio.Task:
    """启动后台调度（幂等）。"""
    return asyncio.create_task(_scheduler_loop())
