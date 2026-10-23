"""衣橱零录入 + AI 每日穿搭 · 真实链路验证（closet-service 8093 已起）

验证：
1. outfit_daily：真实衣橱数据 → 规则推荐 + LLM 润色（成功或降级均记录）
2. closet_scan_item：LLM 视觉识别公网衣物图 → 自动入库
3. 越权拦截：userId 与上下文不一致 → PermissionError
"""
import asyncio
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent.parent))

from app.tools import _current_user_id, closet_scan_item, outfit_daily


async def main():
    print("=" * 60)
    print("1) outfit_daily 真实链路（衣橱已有 2 件）")
    _current_user_id.set("10003")
    out = await outfit_daily.ainvoke({"user_id": "10003", "occasion": "日常"})
    print(out)
    print("-" * 60)

    print("2) outfit_daily 越权拦截（userId 99999 ≠ 上下文 10003）")
    try:
        await outfit_daily.ainvoke({"user_id": "99999", "occasion": "日常"})
        print("!! 未拦截，异常")
    except PermissionError:
        print("PASS: PermissionError 拦截")
    print("-" * 60)

    print("3) closet_scan_item 真实链路（LLM 视觉识别公网衣物图）")
    img = "https://images.unsplash.com/photo-1521572163474-6864f9cf17ab?w=400"  # 白T恤
    out = await closet_scan_item.ainvoke({"image_url": img})
    print(out)
    print("=" * 60)


asyncio.run(main())
