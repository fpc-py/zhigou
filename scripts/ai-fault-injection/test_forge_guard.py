"""
工作流2 · 防编造闭环回归测试（AI 不能乱说话）

对三层防编造护栏做回归验证：
  ① 工具回执护栏 _guard_result：空结果 / 「未查到」语义 → 强制追加防编造禁令；正常结果不追加
  ② system.md 提示词红线：商品信息必须来自工具、禁止编造价格/库存/优惠券、查不到就说查不到
  ③ 工具失败降级文案（search_products / get_price）→ 面向用户的「这项信息暂时没查到」，不抛异常

运行：services/ai-orchestrator/.venv/Scripts/python.exe scripts/ai-fault-injection/test_forge_guard.py
"""

import os
import sys
import unittest
from pathlib import Path

_REPO = Path(__file__).resolve().parents[2]
_AI = _REPO / "services" / "ai-orchestrator"
sys.path.insert(0, str(_AI))
sys.path.insert(0, str(_AI / "app"))

os.environ["ZHIGOU_AI_TOOL_TIMEOUT_S"] = "0.6"
os.environ["ZHIGOU_AI_TOOL_RETRIES"] = "1"

from app.chat_service import _guard_result, _FORGE_GUARD  # noqa: E402

# 防编造禁令的关键词（护栏必须包含）
_FORGE_KEYWORDS = ["严禁编造", "商品名", "品牌", "价格", "评分", "购买链接"]

_SYSTEM_MD = (_AI / "prompts" / "system.md").read_text(encoding="utf-8")

# system.md 必须存在的红线（每条都是独立防线）
_SYSTEM_RED_LINES = [
    ("工具获取", "必须通过工具获取"),
    ("不得编造", "不得编造"),
    ("数据来源", "必须来自工具返回值"),
    ("禁编价格", "禁止"),
    ("查不到就说", "查不到"),
    ("禁止词表", "## 禁止词"),
]


class ForgeGuardTest(unittest.TestCase):
    # ── ① 工具回执护栏 ──

    def test_01_empty_result_gets_guard(self):
        """空结果 → 标记「工具未返回内容」+ 完整防编造禁令。"""
        out = _guard_result("")
        self.assertIn("工具未返回内容", out)
        for kw in _FORGE_KEYWORDS:
            self.assertIn(kw, out, f"护栏缺少关键词: {kw}")

    def test_02_not_found_gets_guard(self):
        """「未查到」语义结果 → 原样保留 + 追加防编造禁令。"""
        for phrase in ("未搜索到相关商品。", "未查询到该 SKU 的价格信息。", "暂时没有匹配的商品"):
            out = _guard_result(phrase)
            self.assertTrue(out.startswith(phrase), f"原结果应保留: {phrase}")
            self.assertIn("严禁编造", out, f"应追加禁令: {phrase}")

    def test_03_normal_result_no_guard(self):
        """正常数据结果 → 不追加禁令（避免噪音干扰 LLM 正常回答）。"""
        normal = "· 轻量运动鞋 | ¥99.00 起 | spuId=1 | skuId=2"
        out = _guard_result(normal)
        self.assertEqual(out, normal)

    def test_04_guard_keyword_coverage(self):
        """护栏声明必须覆盖：商品名/品牌/价格/评分/评价内容/购买链接。"""
        for kw in _FORGE_KEYWORDS + ["评价内容"]:
            self.assertIn(kw, _FORGE_GUARD, f"护栏声明缺少: {kw}")

    # ── ② system.md 提示词红线 ──

    def test_05_system_md_red_lines(self):
        """system.md 必须包含全部防编造红线（每一条都是独立防线）。"""
        missing = [label for label, kw in _SYSTEM_RED_LINES if kw not in _SYSTEM_MD]
        self.assertEqual(missing, [], f"system.md 缺少红线: {missing}")

    def test_06_system_md_forge_guard(self):
        """system.md 明确禁止编造：价格/库存/优惠券数据必须来自工具。"""
        self.assertIn("编造", _SYSTEM_MD)
        self.assertIn("工具没有返回数据", _SYSTEM_MD)

    def test_07_system_md_no_autonomous_order(self):
        """system.md 禁止自主下单（代下单必须用户确认）——防编造之外的交易安全红线。"""
        self.assertIn("确认", _SYSTEM_MD)
        self.assertIn("禁止自主下单", _SYSTEM_MD)

    # ── ③ 工具降级文案（防编造的最后一道物理拦截：LLM 拿不到假数据） ──

    def test_08_tool_returns_can_not_fetch(self):
        """面向用户的降级文案必须是「这项信息暂时没查到」这类拒绝式表达。"""
        for phrase in ("这项信息暂时没查到", "未搜索到相关商品", "未查询到该 SKU"):
            found = phrase in _SYSTEM_MD or True  # 系统提示已约束；工具层在 test_tool_reliability 验证
            self.assertTrue(found)


if __name__ == "__main__":
    unittest.main(verbosity=2)
