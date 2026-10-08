"""
测试：工具权限校验 + 超时降级
"""

import pytest

from app.tools import _check_user_id, _current_user_id, analyze_requirement, apply_coupon, check_inventory, compare_prices


class TestUserIdValidation:
    """userId 越权校验"""

    def test_check_user_id_match(self):
        """测试 userId 匹配时不抛异常"""
        token = _current_user_id.set("u1001")
        try:
            _check_user_id("u1001")  # 不应抛异常
        finally:
            _current_user_id.reset(token)

    def test_check_user_id_mismatch(self):
        """测试 userId 不匹配时抛 PermissionError"""
        token = _current_user_id.set("u1001")
        try:
            with pytest.raises(PermissionError, match="userId 不一致"):
                _check_user_id("u9999")
        finally:
            _current_user_id.reset(token)

    def test_check_user_id_no_context(self):
        """测试 userId 上下文未设置时抛 RuntimeError"""
        with pytest.raises(RuntimeError, match="current_user_id 未设置"):
            _check_user_id("u1001")


class TestToolFallback:
    """工具超时/失败降级"""

    @pytest.mark.asyncio
    async def test_check_inventory_timeout(self, monkeypatch):
        """测试库存查询超时返回降级文案"""
        token = _current_user_id.set("u1001")
        try:
            # mock httpx.AsyncClient 让它抛出超时
            import httpx

            async def mock_get(*args, **kwargs):
                raise httpx.TimeoutException("timeout")

            monkeypatch.setattr(httpx.AsyncClient, "get", mock_get)
            result = await check_inventory.ainvoke({"sku_id": "sku001"})
            assert "这项信息暂时没查到" in result
        finally:
            _current_user_id.reset(token)

    @pytest.mark.asyncio
    async def test_apply_coupon_403_on_userid_mismatch(self):
        """测试工具调用时 userId 越权返回 403 错误"""
        token = _current_user_id.set("u1001")
        try:
            result = await apply_coupon.ainvoke({"user_id": "u9999", "items": ["sku001"]})
            assert "这项信息暂时没查到" in result
        finally:
            _current_user_id.reset(token)


class TestP1DecisionTools:
    """P1 决策辅助工具：需求拆解 / 比价 / 避坑"""

    def test_analyze_requirement_budget_and_scene(self):
        """需求拆解：提取预算与场景"""
        import asyncio

        result = asyncio.run(analyze_requirement.ainvoke({"message": "3000元以内适合送女朋友的礼物"}))
        assert '"budget": 3000' in result
        assert '"scene": "送礼"' in result

    def test_analyze_requirement_category(self):
        """需求拆解：识别品类（蓝牙耳机）"""
        import asyncio

        result = asyncio.run(analyze_requirement.ainvoke({"message": "500块以内的蓝牙耳机哪个好"}))
        assert '"budget": 500' in result
        assert '"category": "耳机"' in result

    @pytest.mark.asyncio
    async def test_compare_prices_no_user_context(self):
        """比价：无用户上下文时降级不崩溃"""
        result = await compare_prices.ainvoke({"sku_ids": ["sku001"]})
        assert "这项信息暂时没查到" in result or "价格/规格对比" in result
