# ADR-0001 · 服务治理与 AI 端口迁移

> 状态：✅ 已采纳 · 日期：2026-10-10 · 关联：`docs/智购-企业级工程化技术方案.md` §1.3 / `docs/智购开发任务差距分析报告.md`

## 背景

P0~P2 开发过程中，服务清单与运行端口随生态扩展持续变化，文档曾长期滞留旧事实（12 服务 / AI :8000 / 5 工具），导致 README/CLAUDE/运维文档相互矛盾。技术方案（v2.0）要求：限界上下文按变更频率切分、AI 推理与交易域分池部署、重要选型留 ADR。

## 决策

1. **服务清单以 `services/` 目录为唯一真相**：当前 16 个服务（15 Java + 1 Python），新增服务必须同步 README 端口表、CLAUDE 目录树、docs/README 索引、release-checklist/gray-release/monitoring/mock-cleanup 五份运维文档。
2. **AI 编排固定 8095**：8000 曾被其他项目进程占用且难以清理（幽灵监听 + 多实例 uvicorn 竞态），迁移至 8095；BFF `service.config.ts` 同步；启动强制 `.venv`（系统 Python 会加载旧代码导致新工具不生效）。
3. **AI 工具清单以 `tools.py` 的 TOOLS 列表为唯一真相**：新工具 = `@tool` 定义 + 显式加入 TOOLS 列表（列表置于文件末尾）；system.md 工具表与 `_ROUTE_RULES` 同步。
4. **文档对齐纪律**：任何影响服务/端口/接口/工具数的变更，交付时必须同步全部受影响文档（progress 变更记录 + CHANGELOG 为强制项）。

## 后果

- 正向：文档与代码一致，新成员/新会话可按 docs/README 阅读路径快速对齐。
- 成本：每次新增服务需改动 7+ 处文档；ADR 需随架构演进追加（ADR-0002 起）。

*本文档路径: `docs/adr/ADR-0001-服务治理与AI端口迁移.md`*
