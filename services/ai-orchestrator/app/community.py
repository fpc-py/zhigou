"""
智购 AI Orchestrator — 社区侧 AI 服务
community/writer：AI 种草文案生成（商品真实数据锚点，禁止编造）
"""
import logging

import httpx
from fastapi import APIRouter, HTTPException
from openai import AsyncOpenAI
from pydantic import BaseModel, Field

from app.config import settings

logger = logging.getLogger(__name__)
router = APIRouter(prefix="/api/v1/community", tags=["community"])


class WriterRequest(BaseModel):
    spuId: str = Field(..., description="商品 SPU ID")
    style: str = Field("日常", description="文案风格：日常/测评/种草/清单")


_STYLE_HINT = {
    "日常": "日常真实体验口吻，第一人称，像朋友分享",
    "测评": "理性测评口吻，分点给出优点与适用人群",
    "种草": "氛围种草口吻，突出场景与心动感",
    "清单": "清单体，先给结论再列要点",
}


async def _get_json(url: str, headers: dict) -> dict:
    async with httpx.AsyncClient(timeout=5.0) as client:
        resp = await client.get(url, headers=headers)
        resp.raise_for_status()
        return resp.json()


async def _load_product(spu_id: str) -> tuple[dict, dict] | None:
    """拉商品详情 + 评价统计；失败返回 None"""
    headers = {"x-user-id": "2107757313435291648"}
    try:
        detail = await _get_json(
            f"{settings.product_service_url}/product/{spu_id}", headers)
        stats = await _get_json(
            f"{settings.product_service_url}/product/review/stats?spuId={spu_id}", headers)
    except Exception as e:
        logger.warning("community writer 加载商品失败 spuId=%s: %s", spu_id, e)
        return None
    d = detail.get("data") or {}
    s = stats.get("data") or {}
    if not d.get("spuId"):
        return None
    return d, s


@router.post("/writer")
async def community_writer(req: WriterRequest):
    loaded = await _load_product(req.spuId)
    if loaded is None:
        raise HTTPException(status_code=404, detail="商品不存在或服务暂不可用")
    detail, stats = loaded

    spu_name = str(detail.get("name") or "该商品")
    price_fen = int(detail.get("priceMin") or 0)
    price_txt = f"¥{price_fen / 100:.2f}"
    avg = stats.get("avgRating") or 0
    total = int(stats.get("total") or 0)
    subtitle = str(detail.get("subtitle") or "")
    sales = int(detail.get("salesVolume") or 0)

    style = req.style if req.style in _STYLE_HINT else "日常"
    hint = _STYLE_HINT[style]

    prompt = (
        f"你是电商平台「智购」的种草笔记文案助手。请为下面的商品写一篇 {style} 风格的种草文案（150~260 字，"
        "中文，可直接作为社区笔记发布，勿含价格谈判/优惠券引导）。\n"
        f"商品信息（均为真实平台数据，必须原样使用，禁止编造或夸大）：\n"
        f"- 商品名：{spu_name}\n"
        f"- 副标题：{subtitle}\n"
        f"- 现价：{price_txt}\n"
        f"- 累计销量：{sales} 件\n"
        f"- 用户评分：{avg} 分（共 {total} 条评价）\n"
        f"写作要求：{hint}。第一段给出使用场景与一句话结论；中间 2~3 个真实卖点（只能基于上述商品信息展开，"
        "不得虚构具体功能参数/包装/赠品/物流时效）；结尾给出适合人群。不得出现「价格仅 xx」「好评返现」等营销违规话术。"
    )

    try:
        client = AsyncOpenAI(base_url=settings.llm_base_url, api_key=settings.llm_api_key)
        resp = await client.chat.completions.create(
            model=settings.llm_model,
            messages=[
                {"role": "system", "content": "你是智购社区的种草笔记文案助手，输出纯文案，不要任何解释或 Markdown。"},
                {"role": "user", "content": prompt},
            ],
            temperature=float(getattr(settings, "llm_temperature", 0.7) or 0.7),
            max_tokens=600,
        )
        draft = (resp.choices[0].message.content or "").strip()
    except Exception as e:
        logger.warning("community writer LLM 失败: %s", e)
        raise HTTPException(status_code=502, detail="文案生成服务暂时不可用，请稍后再试")

    return {
        "spuId": req.spuId,
        "spuName": spu_name,
        "priceFen": price_fen,
        "avgRating": avg,
        "reviewTotal": total,
        "style": style,
        "draft": draft,
    }
