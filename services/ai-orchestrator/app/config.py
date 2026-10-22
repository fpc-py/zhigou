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
    search_service_url: str = "http://localhost:8083"
    product_service_url: str = "http://localhost:8083"
    inventory_service_url: str = "http://localhost:8086"
    user_service_url: str = "http://localhost:8081"
    marketing_service_url: str = "http://localhost:8088"
    cart_service_url: str = "http://localhost:8084"
    order_service_url: str = "http://localhost:8085"
    logistics_service_url: str = "http://localhost:8089"
    aftersale_service_url: str = "http://localhost:8090"

    # ── JWT（用于转发认证） ──
    jwt_secret: str = "change-me-to-a-random-256-bit-string"

    # ── pgvector ──
    pg_dsn: str = "postgresql+asyncpg://zhigou:zhigou123@localhost:5432/zhigou_rag"

    # ── Embedding ──
    embedding_model: str = "text-embedding-v4"
    embedding_dim: int = 1536

    # ── 工具调用可靠性（工作流2 统一治理：超时 / 重试 / 降级） ──
    # ai_tool_timeout_s：HTTP 工具默认超时（秒）；ai_tool_retries：只读 GET 重试次数（写操作不重试防重复下单）；
    # ai_tool_retry_delay_s：重试基础间隔（秒，按尝试次数线性递增）。
    # 模型是否支持 specific function tool_choice（OpenAI 兼容支持；deepseek 仅 auto/none，静默忽略）
    ai_specific_tool_choice: bool = True
    ai_tool_timeout_s: float = 5.0
    ai_tool_retries: int = 1
    ai_tool_retry_delay_s: float = 0.3

    # ── RocketMQ ──
    rocketmq_namesrv: str = "localhost:9876"

    model_config = {"env_prefix": "ZHIGOU_", "env_file": ".env", "extra": "ignore"}


settings = Settings()