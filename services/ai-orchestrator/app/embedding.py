"""
智购 AI Orchestrator — Embedding 客户端

调用 OpenAI 兼容 API（text-embedding-v4），支持单条和批量 embedding。
"""

import logging
from typing import Optional

import openai
from openai import AsyncOpenAI

from .config import settings

logger = logging.getLogger(__name__)


class EmbeddingClient:
    """Embedding 客户端，复用 LLM 的 base_url 和 api_key。"""

    def __init__(
        self,
        base_url: Optional[str] = None,
        api_key: Optional[str] = None,
        model: Optional[str] = None,
    ):
        self.base_url = (base_url or settings.llm_base_url).rstrip("/")
        self.api_key = api_key or settings.llm_api_key
        self.model = model or settings.embedding_model
        self._client: Optional[AsyncOpenAI] = None

    @property
    def client(self) -> AsyncOpenAI:
        if self._client is None:
            self._client = AsyncOpenAI(base_url=self.base_url, api_key=self.api_key)
        return self._client

    async def embed(self, text: str) -> list[float]:
        """单条文本 embedding。"""
        resp = await self.client.embeddings.create(model=self.model, input=text)
        return resp.data[0].embedding

    async def embed_many(self, texts: list[str]) -> list[list[float]]:
        """批量文本 embedding。"""
        resp = await self.client.embeddings.create(model=self.model, input=texts)
        return [d.embedding for d in resp.data]


# 单例
_embedder: Optional[EmbeddingClient] = None


def get_embedder() -> EmbeddingClient:
    global _embedder
    if _embedder is None:
        _embedder = EmbeddingClient()
    return _embedder