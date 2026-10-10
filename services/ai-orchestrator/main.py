"""
智购 AI Orchestrator — FastAPI 入口
"""

import logging
import sys
from pathlib import Path

import uvicorn
from fastapi import FastAPI
from fastapi.middleware.cors import CORSMiddleware

# 确保项目根目录在 sys.path 中，支持直接运行 python main.py
sys.path.insert(0, str(Path(__file__).resolve().parent))

from app.api import router as chat_router
from app.config import settings
from app.fallback_config import init_config as init_fallback_config

from app.rag.router import router as rag_router
from app.community import router as community_router
from app.rag.service import init_db
from app.rag.mq_consumer import start_mq_consumer_thread

# ── 日志配置 ──
logging.basicConfig(
    level=getattr(logging, settings.log_level.upper(), logging.INFO),
    format="%(asctime)s [%(levelname)s] %(name)s: %(message)s",
)

logger = logging.getLogger(__name__)

# ── FastAPI 应用 ──
app = FastAPI(
    title="智购 AI Orchestrator",
    description="AI 原生超级商城 — AI 导购服务",
    version="0.1.0",
)

# ── CORS ──
app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)

# ── 路由注册 ──
app.include_router(chat_router, prefix="/api/v1")
app.include_router(rag_router)
app.include_router(community_router)


@app.on_event("startup")
async def startup():
    """启动时加载降级配置，初始化 pgvector 表和 MQ 消费者。"""
    # 降级配置（本地 yaml，后续换 Nacos）
    init_fallback_config()

    try:
        await init_db()
        logger.info("pgvector 初始化完成")
    except Exception as e:
        logger.warning("pgvector 初始化失败（可稍后重试）: %s", e)

    try:
        start_mq_consumer_thread()
        logger.info("MQ 消费者已启动")
    except Exception as e:
        logger.warning("MQ 消费者启动失败: %s", e)


@app.get("/health")
async def health():
    return {"status": "UP", "service": settings.app_name}


if __name__ == "__main__":
    logger.info("启动 AI Orchestrator，端口: %d", settings.app_port)
    uvicorn.run(
        "main:app",
        host="0.0.0.0",
        port=settings.app_port,
        reload=False,
        log_level=settings.log_level.lower(),
    )