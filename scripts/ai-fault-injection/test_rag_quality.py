"""
工作流2 · RAG 检索质量评测（Top-k 命中率 / 相关性阈值 / 空结果降级）

对 pgvector 商品向量检索做质量评测（先评测后决定是否增强）：
  ① Top-1 / Top-3 命中率：典型商品查询是否召回对应商品
  ② 相关性阈值：命中商品的相似度 score 分布；无关查询的 score 基线（决定过滤阈值）
  ③ 空结果降级：无关/未收录商品查询 → 验证当前返回行为（低分噪音 or 空列表）

运行：services/ai-orchestrator/.venv/Scripts/python.exe scripts/ai-fault-injection/test_rag_quality.py
"""

import asyncio
import json
import os
import sys
from pathlib import Path

_REPO = Path(__file__).resolve().parents[2]
_AI = _REPO / "services" / "ai-orchestrator"

# 显式加载 .env 到环境（Settings 的 env_file 相对 cwd 不可靠）
_env_file = _AI / ".env"
if _env_file.exists():
    for line in _env_file.read_text(encoding="utf-8").splitlines():
        line = line.strip()
        if not line or line.startswith("#") or "=" not in line:
            continue
        k, _, v = line.partition("=")
        if k.startswith("ZHIGOU_") and not os.environ.get(k):
            os.environ[k] = v.strip()

sys.path.insert(0, str(_AI))
sys.path.insert(0, str(_AI / "app"))

from app.config import settings  # noqa: E402
from app.embedding import get_embedder  # noqa: E402
from app.rag.service import init_db, similarity_search  # noqa: E402

# ── 评测查询集（期望命中 spu_id 来自 products_vec 现有 5 条） ──
CASES = [
    {"query": "轻量透气跑鞋", "expected": 9000000000000000010, "tag": "鞋类-运动鞋"},
    {"query": "无线降噪蓝牙耳机", "expected": 9000000000000000011, "tag": "数码-耳机"},
    {"query": "纯棉舒适的T恤", "expected": 9000000000000000012, "tag": "服饰-T恤"},
    {"query": "心率监测运动手表", "expected": 9000000000000000999, "tag": "数码-手表"},
    {"query": "恐龙化石收藏品", "expected": None, "tag": "无关查询-应低分/空"},
    {"query": "生鲜水果", "expected": None, "tag": "库外品类-应低分/空"},
]


async def run() -> dict:
    await init_db()
    embedder = get_embedder()

    results = []
    for case in CASES:
        vec = await embedder.embed(case["query"])
        items = await similarity_search(vec, top_k=5)
        top1 = items[0].spu_id if items else None
        top1_score = items[0].score if items else None
        hit1 = top1 == case["expected"]
        hit3 = case["expected"] in [i.spu_id for i in items[:3]] if case["expected"] else None
        results.append(
            {
                "query": case["query"],
                "tag": case["tag"],
                "expected": case["expected"],
                "top1_hit": hit1,
                "top3_hit": hit3,
                "top1_spu": top1,
                "top1_score": top1_score,
                "top5_scores": [round(i.score, 4) for i in items],
                "top5_spus": [i.spu_id for i in items],
            }
        )

    # 汇总指标
    real_cases = [r for r in results if r["expected"] is not None]
    hit1_rate = sum(1 for r in real_cases if r["top1_hit"]) / len(real_cases)
    hit3_rate = sum(1 for r in real_cases if r["top3_hit"]) / len(real_cases)
    hit_scores = [r["top1_score"] for r in real_cases if r["top1_hit"]]
    unrelated_scores = [r["top1_score"] for r in results if r["expected"] is None and r["top1_score"] is not None]

    summary = {
        "top1_hit_rate": round(hit1_rate, 4),
        "top3_hit_rate": round(hit3_rate, 4),
        "hit_scores": hit_scores,
        "unrelated_top1_scores": unrelated_scores,
        "threshold_probe": {
            "min_hit_score": min(hit_scores) if hit_scores else None,
            "max_unrelated_score": max(unrelated_scores) if unrelated_scores else None,
        },
        "cases": results,
    }
    print(json.dumps(summary, ensure_ascii=False, indent=2))
    return summary


if __name__ == "__main__":
    asyncio.run(run())
