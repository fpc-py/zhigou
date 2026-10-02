"""
智购 AI Orchestrator — 配置模块
"""

from pathlib import Path
from pydantic_settings import BaseSettings


class Settings(BaseSettings):
    # ── 应用 ──
    app_name: str = "ai-orchestrator"
    app_port: int = 8000
    log_level: str = "INFO"

    # ── 系统提示词路径 ──
    system_prompt_path: str = str(
        Path(__file__).resolve().parent.parent.parent.parent / "prompts" / "system.md"
    )

    # ── LLM ──
    llm_base_url: str = "https://api.openai.com/v1"
    llm_api_key: str = ""
    llm_model: str = "gpt-4o-mini"
    llm_temperature: float = 0.7

    # ── 各服务 HTTP 地址 ──
    search_service_url: str = "http://localhost:8085"
    product_service_url: str = "http://localhost:8083"
    inventory_service_url: str = "http://localhost:8086"
    user_service_url: str = "http://localhost:8081"
    marketing_service_url: str = "http://localhost:8087"

    # ── JWT（用于转发认证） ──
    jwt_secret: str = "change-me-to-a-random-256-bit-string"

    # ── pgvector ──
    pg_dsn: str = "postgresql+asyncpg://zhigou:zhigou123@localhost:5432/zhigou_rag"

    # ── Embedding ──
    embedding_model: str = "text-embedding-v4"
    embedding_dim: int = 1536

    # ── RocketMQ ──
    rocketmq_namesrv: str = "localhost:9876"

    model_config = {"env_prefix": "ZHIGOU_", "env_file": ".env", "extra": "ignore"}


settings = Settings()