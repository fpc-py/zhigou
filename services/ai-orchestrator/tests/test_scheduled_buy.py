# -*- coding: utf-8 -*-
"""Agent 定时购测试：确认门/存储/列表/取消/调度执行（到期自动下单、失败不轰炸、周期滚动）"""
import asyncio
from datetime import datetime, timedelta

import pytest

from app import scheduled_buy as sb
from app.tools import scheduled_buy

TMP_BUYS = None  # 由 fixture 注入


@pytest.fixture(autouse=True)
def iso_store(tmp_path, monkeypatch):
    """隔离存储：所有读写落在 tmp_path 下，不污染 data/scheduled_buys.json；用户上下文 mock"""
    monkeypatch.setattr(sb, "_BUYS_FILE", tmp_path / "scheduled_buys.json")
    monkeypatch.setattr(sb, "_DATA_DIR", tmp_path)
    monkeypatch.setattr("app.tools._get_current_user", lambda: "10001")
    yield


async def _invoke(**kw):
    return await scheduled_buy.ainvoke(kw)


@pytest.mark.asyncio
async def test_create_requires_confirm():
    out = await _invoke(action="create", sku_id="9000000000000000020", run_at="10:00", confirm=False)
    assert "待你确认" in out and "不会自动扣款" in out
    assert sb.list_tasks("10001") == []  # 未确认零落库


@pytest.mark.asyncio
async def test_create_missing_params():
    out = await _invoke(action="create", run_at="10:00", confirm=True)
    assert "SKU ID" in out
    out = await _invoke(action="create", sku_id="SKU1", confirm=True)
    assert "run_at" in out


@pytest.mark.asyncio
async def test_create_confirmed_persists():
    out = await _invoke(action="create", sku_id="9000000000000000020", count=2,
                        frequency="weekly", run_at="10:00", weekday="周五", confirm=True)
    assert "定时购已创建" in out and "SKU 9000000000000000020" in out
    tasks = sb.list_tasks("10001")
    assert len(tasks) == 1
    t = tasks[0]
    assert t["frequency"] == "weekly" and t["weekday"] == "周五"
    assert t["status"] == "ACTIVE"
    assert datetime.fromisoformat(t["nextRunAt"]) > datetime.now()


@pytest.mark.asyncio
async def test_list_empty():
    out = await _invoke(action="list")
    assert "还没有定时购任务" in out


@pytest.mark.asyncio
async def test_cancel():
    t = sb.create_task("10001", "SKU1", 1, "daily", "09:00")
    out = await _invoke(action="cancel", task_id=t["id"])
    assert "已取消" in out and sb.list_tasks("10001")[0]["status"] == "CANCELED"
    out = await _invoke(action="cancel", task_id="SB-NOEXIST")
    assert "未找到任务" in out


@pytest.mark.asyncio
async def test_run_due_once_success():
    """到期一次性任务：下单成功后标记 DONE"""
    due = (datetime.now() - timedelta(minutes=1)).isoformat()
    t = sb.create_task("10001", "SKU1", 1, "once", "10:00")
    tasks = sb._load()
    tasks[0]["nextRunAt"] = due
    sb._save(tasks)

    async def fake_create(sku_items=None, coupon_id=None):
        return "✅ 订单已创建，请尽快支付（超时自动关单）：\n· 订单号 2111000000000000001"

    n = await sb.run_due_tasks(fake_create)
    assert n == 1
    updated = sb.list_tasks("10001")[0]
    assert updated["status"] == "DONE"
    assert "订单号 2111000000000000001" in (updated["lastResult"] or "")


@pytest.mark.asyncio
async def test_run_due_weekly_rolls_and_failure_no_hammer():
    """周期任务：成功则滚动 nextRunAt 保持 ACTIVE；失败不标 FAILED（等下个周期）"""
    due = (datetime.now() - timedelta(minutes=1)).isoformat()
    t = sb.create_task("10001", "SKU1", 1, "weekly", "10:00", "周五")
    tasks = sb._load()
    tasks[0]["nextRunAt"] = due
    sb._save(tasks)

    async def fake_fail(sku_items=None, coupon_id=None):
        return "下单失败，请稍后重试"

    n = await sb.run_due_tasks(fake_fail)
    assert n == 1
    updated = sb.list_tasks("10001")[0]
    assert updated["status"] == "ACTIVE"  # 不轰炸：周期任务保持活跃等下一周期
    assert datetime.fromisoformat(updated["nextRunAt"]) > datetime.now()  # 已滚动

    # 成功后滚动且保持活跃
    t2 = sb.create_task("10001", "SKU2", 1, "weekly", "10:00", "周一")
    tasks2 = sb._load()
    for x in tasks2:
        if x["id"] == t2["id"]:
            x["nextRunAt"] = due
    sb._save(tasks2)
    async def fake_ok(sku_items=None, coupon_id=None):
        return "✅ 订单已创建"

    n = await sb.run_due_tasks(fake_ok)
    assert n == 1
    up2 = [x for x in sb.list_tasks("10001") if x["id"] == t2["id"]][0]
    assert up2["status"] == "ACTIVE"
    assert datetime.fromisoformat(up2["nextRunAt"]) > datetime.now()
