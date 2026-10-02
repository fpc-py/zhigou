#! /usr/bin/env python3
"""
智购 · 压测数据准备脚本

向 product-service 插入 N 个 SPU 供压测使用。
业务数据统一用脚本生成，不在 k6 脚本中内联"跑步鞋"这种硬编码商品名。
"""

import asyncio
import httpx
import logging

logging.basicConfig(level=logging.INFO, format="%(asctime)s [%(levelname)s] %(message)s")
logger = logging.getLogger("seed_data")

PRODUCT_URL = "http://localhost:8083"
COUNT = 100  # 生成 100 个 SPU

ADJECTIVES = ["轻量", "专业", "经典", "升级", "便携", "智能", "舒适", "耐用", "时尚", "高性能"]
NOUNS = ["运动鞋", "跑鞋", "耳机", "充电宝", "背包", "手表", "水杯", "T恤", "外套", "笔记本支架"]

async def create_spu(client, idx):
    name = f"{ADJECTIVES[idx % len(ADJECTIVES)]}{NOUNS[idx % len(NOUNS)]}"
    price = 5000 + idx * 1000  # 分
    payload = {
        "categoryId": 1 + (idx % 3),
        "name": name,
        "subtitle": f"{name} — 智购精选",
        "description": f"这是压测用的第 {idx} 个商品：{name}。品质保证，放心购买。",
        "skus": [
            {"specName": "颜色", "specValue": "黑色", "price": price, "stock": 100},
            {"specName": "颜色", "specValue": "白色", "price": price + 500, "stock": 80},
        ],
    }
    resp = await client.post(f"{PRODUCT_URL}/product/spu", json=payload)
    if resp.status_code == 200:
        spu_id = resp.json().get("data", {}).get("spuId")
        logger.info(f"[{idx:4d}/{COUNT}] 已创建: {name} → spuId={spu_id}")
        return spu_id
    else:
        logger.warning(f"[{idx:4d}/{COUNT}] 创建失败: {resp.status_code} {resp.text}")
        return None

async def main():
    logger.info(f"开始生成 {COUNT} 个 SPU……")
    async with httpx.AsyncClient(timeout=10) as client:
        tasks = [create_spu(client, i) for i in range(1, COUNT + 1)]
        results = await asyncio.gather(*tasks)
    created = [r for r in results if r is not None]
    logger.info(f"完成: 成功创建 {len(created)}/{COUNT} 个 SPU")

if __name__ == "__main__":
    asyncio.run(main())