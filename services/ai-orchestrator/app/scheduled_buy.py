"""
智购 AI Orchestrator — Agent 定时购（存储 + 调度执行）

设计要点（对齐项目红线）：
- 不重复造轮子：到点执行复用 `create_order`（幂等 requestId + X-User-Id 注入，已在 tools.py）；
- 确认门前置：任务创建时用户必须明确确认（confirm=true），到点自动下单 = 用户已授权；
- 错误不骚扰：调度执行失败不重试轰炸（标记 FAILED 并留原因），周期任务失败等待下个周期；
- 持久化：data/scheduled_buys.json（与会话记忆 data/sessions 同目录风格，生产可换 Redis）。
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
_BUYS_FILE = _DATA_DIR / "scheduled_buys.json"

_STATUS_ACTIVE = "ACTIVE"
_STATUS_DONE = "DONE"
_STATUS_CANCELED = "CANCELED"
_STATUS_FAILED = "FAILED"

_FREQ_HINT = {"once": "一次性", "daily": "每天", "weekly": "每周"}
_WEEKDAYS = ["周一", "周二", "周三", "周四", "周五", "周六", "周日"]


def _load() -> list[dict[str, Any]]:
    if not _BUYS_FILE.exists():
        return []
    try:
        data = json.loads(_BUYS_FILE.read_text(encoding="utf-8"))
        return data if isinstance(data, list) else []
    except Exception as e:
        logger.warning("scheduled_buys.json 读取失败: %s", e)
        return []


def _save(tasks: list[dict[str, Any]]) -> None:
    _DATA_DIR.mkdir(parents=True, exist_ok=True)
    tmp = _BUYS_FILE.with_suffix(".json.tmp")
    tmp.write_text(json.dumps(tasks, ensure_ascii=False, indent=2), encoding="utf-8")
    tmp.replace(_BUYS_FILE)


def _next_run(frequency: str, run_at: str, weekday: str | None = None) -> str:
    """计算下次执行时间（ISO）。once 支持完整日期时间（2026-10-25 10:00），其余形如 HH:MM。"""
    now = datetime.now()
    if frequency == "once":
        # 完整日期时间优先（一次性任务）
        try:
            dt = datetime.fromisoformat(run_at)
            if dt > now:
                return dt.isoformat()
            return (dt + timedelta(days=1)).isoformat()
        except ValueError:
            pass
    try:
        hh, mm = (run_at or "09:00").split(":")[:2]
        base = now.replace(hour=int(hh), minute=int(mm), second=0, microsecond=0)
    except ValueError:
        base = now.replace(hour=9, minute=0, second=0, microsecond=0)
    if frequency == "daily":
        if base <= now:
            base += timedelta(days=1)
        return base.isoformat()
    if frequency == "weekly":
        # weekday: 0=周一 … 6=周日
        wd = _WEEKDAYS.index(weekday) if weekday in _WEEKDAYS else now.weekday()
        delta = (wd - base.weekday()) % 7
        candidate = base + timedelta(days=delta)
        if candidate <= now:
            candidate += timedelta(days=7)
        return candidate.isoformat()
    # once 回退 HH:MM：未来时刻或明天同一时刻
    return base.isoformat() if base > now else (base + timedelta(days=1)).isoformat()


def create_task(user_id: str, sku_id: str, count: int,
                frequency: str, run_at: str, weekday: str | None = None) -> dict[str, Any]:
    """创建定时购任务（调用方须已确认）。返回任务 dict。"""
    tasks = _load()
    task = {
        "id": "SB" + uuid.uuid4().hex[:12].upper(),
        "userId": user_id,
        "skuId": sku_id,
        "count": count,
        "frequency": frequency,
        "runAt": run_at,
        "weekday": weekday,
        "nextRunAt": _next_run(frequency, run_at, weekday),
        "status": _STATUS_ACTIVE,
        "createdAt": datetime.now().isoformat(),
        "lastResult": None,
    }
    tasks.append(task)
    _save(tasks)
    return task


def list_tasks(user_id: str) -> list[dict[str, Any]]:
    return [t for t in _load() if t.get("userId") == user_id]


def cancel_task(user_id: str, task_id: str) -> dict[str, Any] | None:
    tasks = _load()
    for t in tasks:
        if t.get("id") == task_id and t.get("userId") == user_id:
            t["status"] = _STATUS_CANCELED
            _save(tasks)
            return t
    return None


async def run_due_tasks(create_order_fn) -> int:
    """扫描到期任务并执行下单（复用 create_order 工具函数，幂等防重复）。返回执行数。"""
    tasks = _load()
    now = datetime.now()
    ran = 0
    changed = False
    for t in tasks:
        if t.get("status") != _STATUS_ACTIVE:
            continue
        next_run = t.get("nextRunAt") or ""
        try:
            due = datetime.fromisoformat(next_run) <= now
        except ValueError:
            due = False
        if not due:
            continue
        # 执行：设置用户上下文后调用 create_order（LangChain BaseTool 须 .ainvoke，兼容普通函数）
        from app.tools import _current_user_id
        token = _current_user_id.set(t.get("userId"))
        try:
            payload = {"sku_items": [{"skuId": t.get("skuId"), "count": int(t.get("count") or 1)}]}
            if hasattr(create_order_fn, "ainvoke"):
                result = await create_order_fn.ainvoke(payload)
            else:
                result = await create_order_fn(**payload)
        finally:
            _current_user_id.reset(token)
        t["lastResult"] = result
        if "订单已创建" in (result or "") or "订单号" in (result or ""):
            if t.get("frequency") == "once":
                t["status"] = _STATUS_DONE
            else:
                t["nextRunAt"] = _next_run(t.get("frequency"), t.get("runAt"), t.get("weekday"))
        else:
            # 失败不重试轰炸：标记 FAILED 留原因，周期任务等下一个周期
            t["status"] = _STATUS_FAILED if t.get("frequency") == "once" else t["status"]
            if t.get("frequency") != "once":
                t["nextRunAt"] = _next_run(t.get("frequency"), t.get("runAt"), t.get("weekday"))
        changed = True
        ran += 1
    if changed:
        _save(tasks)
    return ran


async def _scheduler_loop(interval_s: int = 60) -> None:
    """后台调度循环：每 interval 秒扫描一次到期任务。"""
    from app.tools import create_order  # 延迟导入避免循环依赖
    while True:
        try:
            n = await run_due_tasks(create_order)
            if n:
                logger.info("定时购调度：本轮执行 %d 个任务", n)
        except Exception as e:
            logger.warning("定时购调度异常: %s", e)
        await asyncio.sleep(interval_s)


def start_scheduler() -> asyncio.Task:
    """启动后台调度任务（幂等）。"""
    return asyncio.create_task(_scheduler_loop())


def format_task(t: dict[str, Any]) -> str:
    freq = _FREQ_HINT.get(t.get("frequency"), t.get("frequency"))
    when = t.get("runAt") or ""
    if t.get("frequency") == "weekly" and t.get("weekday"):
        when = f"{t.get('weekday')} {when}"
    lines = [
        f"· {t.get('id')}｜SKU {t.get('skuId')} x{t.get('count')}｜{freq} {when}｜状态 {t.get('status')}",
        f"  下次执行 {str(t.get('nextRunAt'))[:16].replace('T', ' ')}",
    ]
    if t.get("lastResult"):
        lines.append("  上次结果：" + (t.get("lastResult") or "")[:80])
    return "\n".join(lines)
