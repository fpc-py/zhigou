"""
智购 AI Orchestrator — 降级配置模块

从本地 YAML 文件读取降级开关和超时配置（生产环境后续接 Nacos）。
支持运行时动态重载。
"""

import logging
import threading
from pathlib import Path
from typing import Optional

logger = logging.getLogger(__name__)

# ── 默认 YAML 路径 ──
_DEFAULT_CONFIG_PATH = Path(__file__).resolve().parent.parent.parent.parent / "config" / "fallback.yml"

# ── 全局状态 ──
_config_path: Path = _DEFAULT_CONFIG_PATH
_ai_enabled: bool = True
_llm_timeout_ms: int = 1500
_lock = threading.Lock()


def _load_yaml() -> dict:
    """读取并解析 YAML 文件，失败时返回空 dict。"""
    try:
        import yaml
        path = _config_path
        if not path.exists():
            logger.warning("降级配置文件不存在: %s，使用默认值", path)
            return {}
        with open(path, "r", encoding="utf-8") as f:
            data = yaml.safe_load(f)
            return data if isinstance(data, dict) else {}
    except ImportError:
        logger.warning("PyYAML 未安装，降级配置使用默认值")
        return {}
    except Exception as e:
        logger.warning("降级配置加载失败: %s，使用默认值", e)
        return {}


def _parse(data: dict) -> None:
    """从 dict 解析配置并更新全局状态。"""
    global _ai_enabled, _llm_timeout_ms

    if not data:
        # 空配置 = 恢复出厂默认值
        _ai_enabled = True
        _llm_timeout_ms = 1500
        return

    ai_cfg = data.get("ai", {})
    if isinstance(ai_cfg, dict) and "enabled" in ai_cfg:
        _ai_enabled = bool(ai_cfg["enabled"])

    llm_cfg = data.get("llm", {})
    if isinstance(llm_cfg, dict) and "timeout_ms" in llm_cfg:
        _llm_timeout_ms = int(llm_cfg["timeout_ms"])


def init_config(config_path: Optional[str] = None) -> None:
    """初始化配置（启动时调用）。"""
    global _config_path
    if config_path:
        _config_path = Path(config_path)
    with _lock:
        data = _load_yaml()
        _parse(data)
    logger.info("降级配置已加载: ai.enabled=%s, llm.timeout_ms=%d", _ai_enabled, _llm_timeout_ms)


def reload_config() -> None:
    """运行时重载配置。"""
    with _lock:
        data = _load_yaml()
        _parse(data)
    logger.info("降级配置已重载: ai.enabled=%s, llm.timeout_ms=%d", _ai_enabled, _llm_timeout_ms)


def ai_enabled() -> bool:
    """AI 导购是否启用。"""
    with _lock:
        return _ai_enabled


def llm_timeout_ms() -> int:
    """LLM 超时毫秒数。"""
    with _lock:
        return _llm_timeout_ms