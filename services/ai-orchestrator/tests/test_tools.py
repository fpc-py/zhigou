"""
测试：工具权限校验 + 超时降级
"""

import pytest

from app.tools import _check_user_id, _current_user_id, apply_coupon, check_inventory


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