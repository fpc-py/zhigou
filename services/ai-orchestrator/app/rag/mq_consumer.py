"""
智购 AI Orchestrator — RocketMQ 消费者

监听 PRODUCT_CHANGED Topic，收到后调用 embedding 模型重新计算向量并 upsert 到 pgvector。
幂等：spu_id UNIQUE + ON CONFLICT DO UPDATE，重复消息只覆盖不重复。
"""

import asyncio
import json
import logging
import threading
from typing import Optional

from ..config import settings
from ..embedding import get_embedder
from .service import upsert_vector, init_db

logger = logging.getLogger(__name__)

# ── 异步处理函数（供 MQ 回调调用） ──

_loop: Optional[asyncio.AbstractEventLoop] = None


def _get_loop() -> asyncio.AbstractEventLoop:
    global _loop
    if _loop is None:
        try:
            _loop = asyncio.get_running_loop()
        except RuntimeError:
            _loop = asyncio.new_event_loop()
            asyncio.set_event_loop(_loop)
    return _loop


async def _handle_message_async(body: str) -> None:
    """异步处理单条 MQ 消息。"""
    try:
        payload = json.loads(body)
        spu_id = payload.get("spuId")
        name = payload.get("name", "")
        subtitle = payload.get("subtitle", "")
        description = payload.get("description", "")

        if not spu_id:
            logger.warning("MQ 消息缺少 spuId: %s", body[:200])
            return

        # 拼接文本
        text_parts = [name, subtitle, description]
        text = " ".join(p for p in text_parts if p).strip()
        if not text:
            logger.warning("商品文本为空，跳过: spuId=%s", spu_id)
            return

        # 调 embedding 模型
        embedder = get_embedder()
        embedding = await embedder.embed(text)

        # upsert 到 pgvector
        await upsert_vector(int(spu_id), text, embedding)
        logger.info("商品向量更新成功: spuId=%s, text_len=%d", spu_id, len(text))

    except Exception as e:
        logger.error("MQ 消息处理失败: %s", e, exc_info=True)


# ── 同步回调包装（RocketMQ C++ SDK 回调是同步的） ──


def _message_handler(msg) -> None:
    """同步的 MQ 回调，将控制权交给异步循环。"""
    try:
        body = msg.body.decode("utf-8") if isinstance(msg.body, bytes) else msg.body
        loop = _get_loop()
        if loop.is_running():
            asyncio.run_coroutine_threadsafe(_handle_message_async(body), loop)
        else:
            loop.run_until_complete(_handle_message_async(body))
    except Exception as e:
        logger.error("MQ 回调异常: %s", e, exc_info=True)


# ── 启动消费者线程 ──

_mq_thread: Optional[threading.Thread] = None


def start_mq_consumer_thread() -> None:
    """
    在后台线程中启动 RocketMQ 消费者。
    如果 rocketmq-client-python 未安装，静默跳过。
    """
    global _mq_thread

    try:
        from rocketmq.client import PushConsumer  # type: ignore
    except ImportError:
        logger.warning(
            "rocketmq-client-python 未安装，MQ 消费者跳过。"
            " 如需启用: pip install rocketmq-client-python"
        )
        return

    def _run():
        try:
            consumer = PushConsumer("rag-consumer-group")
            consumer.set_namesrv_addr(settings.rocketmq_namesrv)
            consumer.subscribe("PRODUCT_CHANGED", "*")
            consumer.register_message_callback(_message_handler)
            logger.info("MQ 消费者已连接: %s", settings.rocketmq_namesrv)
            consumer.start()
        except Exception as e:
            logger.error("MQ 消费者运行失败: %s", e)

    _mq_thread = threading.Thread(target=_run, name="mq-consumer", daemon=True)
    _mq_thread.start()