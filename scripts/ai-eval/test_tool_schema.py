# -*- coding: utf-8 -*-
"""AI 评测回归集 · 1/3 工具注册全量校验（防 #144 漏注册重演）

独立可跑（零外部依赖、零 LLM key）：TOOLS 全量 convert_to_openai_tool +
名称唯一 + 参数 schema 完整。故意删工具注册 → 本脚本必红（回归有效性自证）。
"""
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[2] / "services" / "ai-orchestrator"))

from langchain_core.utils.function_calling import convert_to_openai_tool  # noqa: E402

from app.tools import TOOLS  # noqa: E402

EXPECTED = 35


def main() -> int:
    failures = []
    names = [getattr(t, "name", None) for t in TOOLS]
    if len(names) != EXPECTED:
        failures.append(f"TOOLS 应 {EXPECTED} 个，实际 {len(names)}")
    if len(set(names)) != len(names):
        dup = {n for n in names if names.count(n) > 1}
        failures.append(f"工具名重复: {dup}")
    for t in TOOLS:
        try:
            schema = convert_to_openai_tool(t)
        except Exception as e:  # noqa: BLE001
            failures.append(f"{getattr(t, 'name', '?')} convert 失败: {e}")
            continue
        fn = schema.get("function", {})
        if schema.get("type") != "function" or not fn.get("name"):
            failures.append(f"{getattr(t, 'name', '?')} schema 缺 type/name")
        params = fn.get("parameters", {})
        if not isinstance(params, dict) or "properties" not in params:
            failures.append(f"{fn.get('name')} 缺 parameters.properties")
    if failures:
        print("FAIL ai-eval/schema")
        for f in failures:
            print(" -", f)
        return 1
    print(f"PASS ai-eval/schema: {EXPECTED} 工具 schema 全量转换 + 名称唯一 + 参数完整")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
