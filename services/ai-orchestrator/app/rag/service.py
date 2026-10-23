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

# text-embedding-v4 输出维度为 1024（曾误配 1536 致 upsert 维度不匹配，见 docs/测试问题bug记录.md #33）
EMBEDDING_DIM = 1024


async def init_db() -> None:
    """启动时执行一次：创建 extension、表、索引。"""
    await _execute("CREATE EXTENSION IF NOT EXISTS vector")
    await _execute("""
        CREATE TABLE IF NOT EXISTS products_vec (
            id         BIGSERIAL PRIMARY KEY,
            spu_id     BIGINT NOT NULL UNIQUE,
            embedding  vector({dim}) NOT NULL,
            text       TEXT NOT NULL,
            updated_at TIMESTAMPTZ DEFAULT now()
        )
    """.format(dim=EMBEDDING_DIM))
    # 工作流2 实测：ivfflat(lists=100) 在小数据量下严重退化（probe=1 只扫 1 个 list，
    # 大部分 list 为空 → 真实查询返回 0 行，检索不稳定）。pgvector 0.8.7 支持 HNSW，
    # 无 probe/lists 敏感性、小数据精确度高 → 换 HNSW。
    await _execute("""
        DROP INDEX IF EXISTS idx_products_vec_embedding
    """)
    await _execute("""
        CREATE INDEX IF NOT EXISTS idx_products_vec_embedding_hnsw
        ON products_vec USING hnsw (embedding vector_cosine_ops)
        WITH (m = 16, ef_construction = 64)
    """)
    logger.info("pgvector 表已就绪（HNSW 索引）")


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


async def keyword_search(query: str, top_k: int = 10) -> list[RetrieveItem]:
    """
    关键词兜底检索（Embedding 端点不可用时的降级路径）。

    1. 对 products_vec.text 做 ILIKE 命中，按更新时间倒序返回；
    2. 无关键词命中（如首页推荐 query「热门推荐」）时，返回最新 top_k 条
       （“热门 ≈ 最新”兜底语义），保证首页 AI 推荐区块有数据。
    语义检索失败（如 DeepSeek 无 embeddings 端点）时使用，
    保证 RAG 接口永不 500、首页 AI 推荐有兜底数据。
    """
    rows = await _fetch(
        """
        SELECT spu_id, text, 1.0 AS score
        FROM products_vec
        WHERE text ILIKE '%' || $1 || '%'
        ORDER BY updated_at DESC
        LIMIT $2
        """,
        query,
        top_k,
    )
    if rows:
        return [
            RetrieveItem(spu_id=row["spu_id"], text=row["text"], score=round(row["score"], 4))
            for row in rows
        ]
    rows = await _fetch(
        """
        SELECT spu_id, text, 1.0 AS score
        FROM products_vec
        ORDER BY updated_at DESC
        LIMIT $1
        """,
        top_k,
    )
    return [
        RetrieveItem(spu_id=row["spu_id"], text=row["text"], score=round(row["score"], 4))
        for row in rows
    ]


async def similarity_search(
    query_embedding: list[float],
    top_k: int = 10,
    min_score: float = 0.50,
) -> list[RetrieveItem]:
    """
    Cosine 相似度检索（HNSW 索引）。使用 <=> 算子（余弦距离）。

    工作流2 检索质量增强：
    - min_score 相关性阈值：低于阈值(0.50)的低相关结果直接过滤，避免「强行检索到无关商品」
      让 LLM 拿噪音数据作答（评测基线：无关品类查询最高分仅 0.28-0.41，真实命中 ≥0.55）。
    - 阈值过滤后为空 → 返回空列表，由上层（防编造护栏）告知用户查不到，不编造。
    """
    vec_str = f"[{','.join(str(v) for v in query_embedding)}]"
    rows = await _fetch(
        """
        SELECT spu_id, text, 1 - (embedding <=> $1::vector) AS score
        FROM products_vec
        WHERE 1 - (embedding <=> $1::vector) >= $3
        ORDER BY embedding <=> $1::vector
        LIMIT $2
        """,
        vec_str,
        top_k,
        min_score,
    )
    return [
        RetrieveItem(spu_id=row["spu_id"], text=row["text"], score=round(row["score"], 4))
        for row in rows
    ]