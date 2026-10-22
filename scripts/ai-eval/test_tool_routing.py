# -*- coding: utf-8 -*-
"""AI 评测回归集 · 2/3 确定性路由全量回归（含历史坑固化）

断言「确定性路由」14 个工具 × ≥2 正向样例命中，负样例不误路由。
固化历史坑：#71（客服不被售后/物流截胡）、#76（降价提醒≠商家定价）、
#77 同关键词多语义清单。

独立可跑（零外部依赖、零 LLM key）。
"""
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[2] / "services" / "ai-orchestrator"))

from app.chat_service import _route_tool  # noqa: E402

# 正向样例：工具 → ≥2 触发词（与 _ROUTE_RULES 对齐）
POSITIVE = {
    "customer_service": ["我要转人工投诉", "运费怎么算", "发票怎么开", "会员有什么权益", "几天发货"],
    "reminders": ["有什么提醒吗", "提醒我", "该补货了", "换季了该买什么衣服"],
    "gift_assistant": ["送女朋友什么礼物好", "情人节送什么"],
    "after_sale_manager": ["我要退货", "帮我退款", "处理售后"],
    "aftersale_assistant": ["商品破损怎么办", "申请售后"],
    "logistics_tracker": ["我的快递到哪了", "包裹什么时候到"],
    "scheduled_buy": ["帮我定时购", "每周买一次", "到点自动买"],
    "usage_cycle_assistant": ["快用完了", "还剩多少"],
    "groupbuy_finder": ["帮我拼团", "拼单"],
    "sales_forecast": ["我是商家，预测下销量", "该备多少货", "选品建议"],
    "dynamic_pricing": ["定价建议", "涨价还是降价", "调价"],
    "marketing_plan": ["营销方案", "做活动促销建议", "推广方案"],
    "review_assistant": ["差评怎么回", "待回复评论"],
    "price_alert": ["降价提醒", "降价了没", "盯价"],
}

# 负样例：不命中（语义隔离 + 历史坑回归）
NEGATIVE = [
    ("人工客服投诉快递一直没到", "customer_service"),          # #71：客服优先，非 logistics
    ("降价提醒", "price_alert"),                                # #76：盯价，非 dynamic_pricing
    ("我要退货", "after_sale_manager"),                         # 售后动作，非客服
    ("拼团价多少", "groupbuy_finder"),                          # 拼团，非比价/降价
    ("补货提醒", "reminders"),                                  # 主动提醒，非商家补货工具
]


def main() -> int:
    failures = []
    for tool, samples in POSITIVE.items():
        if not samples:
            continue
        for q in samples:
            got = _route_tool(q)
            if got != tool:
                failures.append(f"[正向] '{q}' → 期望 {tool}，实际 {got}")
    # 每工具至少 2 样例
    for tool, samples in POSITIVE.items():
        if len(samples) < 2:
            failures.append(f"{tool} 正向样例不足 2 条（{len(samples)}）")
    for q, expected in NEGATIVE:
        got = _route_tool(q)
        if got != expected:
            failures.append(f"[负向] '{q}' → 期望 {expected}，实际 {got}")
    if failures:
        print("FAIL ai-eval/routing")
        for f in failures:
            print(" -", f)
        return 1
    total = sum(len(v) for v in POSITIVE.values()) + len(NEGATIVE)
    print(f"PASS ai-eval/routing: {total} 断言（{len(POSITIVE)} 工具正样例 + {len(NEGATIVE)} 负样例含 #71/#76 回归）")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
