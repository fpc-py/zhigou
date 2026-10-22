"""
测试：AI 售后管家 after_sale_manager（overview 聚合诊断 + apply 确认门）
"""

import pytest

from app.tools import after_sale_manager


class TestAfterSaleManagerOverview:
    """overview 聚合诊断"""

    @pytest.mark.asyncio
    async def test_overview_aggregates_three_sources(self, monkeypatch):
        """三路聚合：处理中售后 + 物流延误 + 质保临近"""
        import app.tools as tools

        async def mock_get(url, *a, **kw):
            if "/aftersale/mine" in url:
                return {"data": [
                    {"aftersaleNo": "AS20261022001", "type": "refund", "status": "PENDING", "amount": 4900},
                ]}
            if "/delay-alerts" in url:
                return {"data": [
                    {"shipmentNo": "SF20261022", "reason": "运输停滞 3 天"},
                ]}
            if "/warranty/alerts" in url:
                return {"data": [
                    {"productName": "蓝牙耳机", "status": "EXPIRING_SOON", "daysLeft": 12},
                ]}
            return {"data": []}

        monkeypatch.setattr(tools, "_http_get", mock_get)
        monkeypatch.setattr(tools, "_get_current_user", lambda: "10001")

        out = await after_sale_manager.ainvoke({"action": "overview"})
        assert "售后健康度诊断" in out
        assert "处理中售后 1 单" in out
        assert "AS20261022001" in out
        assert "物流延误预警 1 单" in out
        assert "SF20261022" in out
        assert "质保临近/过期 1 件" in out
        assert "蓝牙耳机" in out

    @pytest.mark.asyncio
    async def test_overview_empty_all(self, monkeypatch):
        """三路全空 → 健康提示（不报错）"""
        import app.tools as tools

        async def mock_get(url, *a, **kw):
            return {"data": []}

        monkeypatch.setattr(tools, "_http_get", mock_get)
        monkeypatch.setattr(tools, "_get_current_user", lambda: "10001")

        out = await after_sale_manager.ainvoke({"action": "overview"})
        assert "暂无售后单、物流延误或质保风险" in out

    @pytest.mark.asyncio
    async def test_overview_downstream_down(self, monkeypatch):
        """下游异常 → 友好降级"""
        import app.tools as tools

        async def mock_get(url, *a, **kw):
            raise ConnectionError("refused")

        monkeypatch.setattr(tools, "_http_get", mock_get)
        out = await after_sale_manager.ainvoke({"action": "overview"})
        assert "暂时不可用" in out


class TestAfterSaleManagerApply:
    """apply 确认门"""

    @pytest.mark.asyncio
    async def test_apply_without_confirm_does_not_write(self, monkeypatch):
        """未确认 → 只返回摘要，不调写接口"""
        import app.tools as tools

        written = []

        async def mock_post(url, *a, **kw):
            written.append(url)
            return {"data": {}}

        monkeypatch.setattr(tools, "_http_post", mock_post)
        monkeypatch.setattr(tools, "_get_current_user", lambda: "10001")

        out = await after_sale_manager.ainvoke({"action": "apply", "order_no": "20261022001", "type": "refund", "issue": "商品损坏"})
        assert "待你确认" in out
        assert "确认申请" in out
        assert len(written) == 0, "未确认时不应产生任何写调用"

    @pytest.mark.asyncio
    async def test_apply_confirmed_uses_order_amount(self, monkeypatch):
        """确认后：金额取订单服务（防编造），POST /aftersale/apply 提交"""
        import app.tools as tools

        posts = []

        async def mock_get(url, *a, **kw):
            if "/order/" in url:
                return {"data": {"orderNo": "20261022001", "payAmount": 4900}}
            return {"data": []}

        async def mock_post(url, *a, **kw):
            posts.append((url, kw.get("json_data")))
            return {"data": {"aftersaleNo": "AS20261022002", "status": "PENDING"}}

        monkeypatch.setattr(tools, "_http_get", mock_get)
        monkeypatch.setattr(tools, "_http_post", mock_post)
        monkeypatch.setattr(tools, "_get_current_user", lambda: "10001")

        out = await after_sale_manager.ainvoke({"action": "apply", "order_no": "20261022001", "type": "refund", "issue": "商品损坏", "confirm": True})
        assert "AS20261022002" in out
        assert "¥49.00" in out
        assert len(posts) == 1
        url, body = posts[0]
        assert "/aftersale/apply" in url
        assert body["amount"] == 4900, "金额应来自订单服务而非模型编造"
        assert body["type"] == "refund"

    @pytest.mark.asyncio
    async def test_apply_missing_params(self, monkeypatch):
        """缺参数 → 友好提示"""
        import app.tools as tools

        monkeypatch.setattr(tools, "_get_current_user", lambda: "10001")
        out = await after_sale_manager.ainvoke({"action": "apply", "order_no": "", "type": ""})
        assert "订单号" in out

    @pytest.mark.asyncio
    async def test_apply_order_not_found(self, monkeypatch):
        """订单查不到金额 → 拒绝发起（防编造）"""
        import app.tools as tools

        async def mock_get(url, *a, **kw):
            return {"data": {}}

        monkeypatch.setattr(tools, "_http_get", mock_get)
        monkeypatch.setattr(tools, "_get_current_user", lambda: "10001")
        out = await after_sale_manager.ainvoke({"action": "apply", "order_no": "999", "type": "refund", "confirm": True})
        assert "未查到订单" in out
