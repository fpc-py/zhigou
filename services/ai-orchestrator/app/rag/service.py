"""
智购 AI Orchestrator — pgvector 向量检索服务

启动时自动建表，提供 upsert / similarity_search / batch_upsert。
"""

import logging
from typing import Optional

import asyncpg

from .models import RetrieveItem
from ..config import settings

logger = logging.getLogger(__name__)

# ── 连接池 ──
_pool: Optional[asyncpg.Pool] = None


async def get_pool() -> asyncpg.Pool:
    global _pool
    if _pool is None:
        dsn = settings.pg_dsn.replace("postgresql+asyncpg://", "postgresql://")
        _pool = await asyncpg.create_pool(dsn=dsn, min_size=2, max_size=10)
        logger.info("pgvector 连接池已创建")
    return _pool


async def _execute(sql: str, *args) -> None:
    pool = await get_pool()
    async with pool.acquire() as conn:
        await conn.execute(sql, *args)


async def _fetch(sql: str, *args) -> list[asyncpg.Record]:
    pool = await get_pool()
    async with pool.acquire() as conn:
        return await conn.fetch(sql, *args)


# ── DDL ──


async def init_db() -> None:
    """启动时执行一次：创建 extension、表、索引。"""
    await _execute("CREATE EXTENSION IF NOT EXISTS vector")
    await _execute("""
        CREATE TABLE IF NOT EXISTS products_vec (
            id         BIGSERIAL PRIMARY KEY,
            spu_id     BIGINT NOT NULL UNIQUE,
            embedding  vector(1536) NOT NULL,
            text       TEXT NOT NULL,
            updated_at TIMESTAMPTZ DEFAULT now()
        )
    """)
    await _execute("""
        CREATE INDEX IF NOT EXISTS idx_products_vec_embedding
        ON products_vec USING ivfflat (embedding vector_cosine_ops)
        WITH (lists = 100)
    """)
    logger.info("pgvector 表已就绪")


# ── 写入 ──


async def upsert_vector(spu_id: int, text: str, embedding: list[float]) -> None:
    """
    单条 upsert。重复 spu_id 会覆盖。
    幂等保证：spu_id UNIQUE 约束 + ON CONFLICT DO UPDATE。
    """
    vec_str = f"[{','.join(str(v) for v in embedding)}]"
    await _execute(
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


async def batch_upsert(records: list[tuple[int, str, list[float]]]) -> int:
    """
    批量 upsert。每条为 (spu_id, text, embedding)。
    返回影响行数。
    """
    if not records:
        return 0

    pool = await get_pool()
    count = 0
    async with pool.acquire() as conn:
        for spu_id, text, embedding in records:
            vec_str = f"[{','.join(str(v) for v in embedding)}]"
            r = await conn.execute(
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
            # r 形如 "INSERT 0 1"
            count += 1
    return count


# ── 检索 ──


async def similarity_search(query_embedding: list[float], top_k: int = 10) -> list[RetrieveItem]:
    """
    Cosine 相似度检索。使用 <=> 算子（余弦距离）。
    返回按相似度降序的 RetrieveItem 列表。
    """
    vec_str = f"[{','.join(str(v) for v in query_embedding)}]"
    rows = await _fetch(
        """
        SELECT spu_id, text, 1 - (embedding <=> $1::vector) AS score
        FROM products_vec
        ORDER BY embedding <=> $1::vector
        LIMIT $2
        """,
        vec_str,
        top_k,
    )
    return [
        RetrieveItem(spu_id=row["spu_id"], text=row["text"], score=round(row["score"], 4))
        for row in rows
    ]