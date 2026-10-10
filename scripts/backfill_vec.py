#! /usr/bin/env python3
"""
智购 · 全量商品向量回填脚本

从 product-service 拉取 SPU 列表，调 embedding 模型计算向量，写入 pgvector。

用法:
    python scripts/backfill_vec.py --limit 100
    python scripts/backfill_vec.py --limit 100 --product-url http://localhost:8083
    python scripts/backfill_vec.py --limit 100 --pg-dsn postgresql://zhigou:zhigou123@localhost:5432/zhigou_rag

依赖:
    pip install httpx asyncpg openai pydantic-settings python-dotenv

说明:
    - embedding 配置（ZHIGOU_LLM_BASE_URL / ZHIGOU_LLM_API_KEY / ZHIGOU_EMBEDDING_MODEL）
      优先取环境变量，其次自动加载 services/ai-orchestrator/.env（与 AI 服务共享口径）。
    - text-embedding-v4 输出 1024 维，pgvector 表 products_vec.embedding 为 vector(1024)。
"""

import argparse
import asyncio
import logging
import os
import sys
from pathlib import Path
from typing import Any

import httpx

logging.basicConfig(
    level=logging.INFO,
    format="%(asctime)s [%(levelname)s] %(message)s",
)
logger = logging.getLogger("backfill_vec")

# ── 默认配置（与 ai-orchestrator 共享语义） ──
DEFAULT_PRODUCT_URL = "http://localhost:8083"
DEFAULT_PG_DSN = "postgresql://zhigou:zhigou123@localhost:5432/zhigou_rag"


def _load_llm_env() -> None:
    """加载 LLM/embedding 配置：优先环境变量，缺失时读 ai-orchestrator/.env。"""
    required = ("ZHIGOU_LLM_BASE_URL", "ZHIGOU_LLM_API_KEY", "ZHIGOU_EMBEDDING_MODEL")
    if all(os.getenv(k) for k in required):
        return
    env_path = Path(__file__).resolve().parents[1] / "services" / "ai-orchestrator" / ".env"
    if env_path.exists():
        try:
            from dotenv import load_dotenv

            load_dotenv(env_path)
            logger.info("已从 %s 加载 LLM/embedding 配置", env_path)
        except ImportError:
            logger.warning("未安装 python-dotenv，跳过 .env 加载（请手动设置 %s）", ", ".join(required))
    else:
        logger.warning("未找到 %s，请设置环境变量 %s", env_path, ", ".join(required))


async def fetch_products(product_url: str, limit: int) -> list[dict[str, Any]]:
    """从 product-service 获取 SPU 列表（内网透传头 X-User-Id 满足 JWT 过滤器）。"""
    url = f"{product_url}/product/page?pageNum=1&pageSize={limit}"
    headers = {"X-User-Id": "0"}  # 内网数据回填无真实用户上下文，走透传头口径
    async with httpx.AsyncClient(timeout=10) as client:
        resp = await client.get(url, headers=headers)
        resp.raise_for_status()
        data = resp.json()
        records = data.get("data", {}).get("records", [])
        logger.info("从 product-service 获取到 %d 条商品", len(records))
        return records


async def embed_products(
    texts: list[str],
    batch_size: int = 20,
) -> list[list[float]]:
    """批量调 LLM embedding 端点（OpenAI 兼容）。"""
    import openai

    client = openai.AsyncOpenAI(
        base_url=os.getenv("ZHIGOU_LLM_BASE_URL", "https://api.openai.com/v1"),
        api_key=os.getenv("ZHIGOU_LLM_API_KEY", ""),
    )
    model = os.getenv("ZHIGOU_EMBEDDING_MODEL", "text-embedding-v4")

    all_embeddings: list[list[float]] = []
    for i in range(0, len(texts), batch_size):
        batch = texts[i : i + batch_size]
        resp = await client.embeddings.create(model=model, input=batch)
        all_embeddings.extend(d.embedding for d in resp.data)
        logger.info("  embedding 进度: %d/%d", min(i + batch_size, len(texts)), len(texts))
    return all_embeddings


async def upsert_to_pg(pg_dsn: str, records: list[tuple[int, str, list[float]]]) -> int:
    """批量 upsert 到 pgvector。"""
    import asyncpg

    conn = await asyncpg.connect(dsn=pg_dsn)
    try:
        count = 0
        for spu_id, text, embedding in records:
            vec_str = f"[{','.join(str(v) for v in embedding)}]"
            await conn.execute(
                """
                INSERT INTO products_vec (spu_id, embedding, text, updated_at)
                VALUES ($1, $2::vector, $3, now())
                ON CONFLICT (spu_id)
                DO UPDATE SET embedding = $2::vector, text = $3, updated_at = now()
                """,
                spu_id,
                vec_str,
                text,
            )
            count += 1
        return count
    finally:
        await conn.close()


async def main():
    parser = argparse.ArgumentParser(description="商品向量全量回填")
    parser.add_argument("--limit", type=int, default=100, help="回填商品数上限")
    parser.add_argument("--product-url", default=DEFAULT_PRODUCT_URL, help="product-service 地址")
    parser.add_argument("--pg-dsn", default=DEFAULT_PG_DSN, help="PostgreSQL DSN")
    args = parser.parse_args()

    _load_llm_env()
    logger.info("开始全量回填，上限 %d 条", args.limit)

    # 1. 获取商品列表
    products = await fetch_products(args.product_url, args.limit)
    if not products:
        logger.warning("未获取到商品数据，请确认 product-service 已启动且有数据")
        return

    # 2. 拼接文本 + 去重
    texts: list[str] = []
    records: list[tuple[int, str, list[float]]] = []
    for p in products:
        spu_id = p.get("spuId")
        name = p.get("name", "")
        subtitle = p.get("subtitle", "")
        description = p.get("description", "")
        text = " ".join(part for part in [name, subtitle, description] if part).strip()
        if not text or not spu_id:
            continue
        texts.append(text)
        records.append((int(spu_id), text, []))

    # 如果已有 embedding，跳过已存在的（简单处理：全部重算）
    logger.info("待计算商品 %d 条", len(records))

    # 3. 批量 embedding
    embeddings = await embed_products(texts)
    records = [
        (spu_id, text, emb)
        for (spu_id, text, _), emb in zip(records, embeddings)
    ]

    # 4. upsert 到 pgvector
    count = await upsert_to_pg(args.pg_dsn, records)
    logger.info("回填完成: %d 条商品已写入 pgvector", count)


if __name__ == "__main__":
    asyncio.run(main())