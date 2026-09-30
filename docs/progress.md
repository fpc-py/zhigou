# 项目进度追踪

> 每完成一个可验证单元，更新本文档。Claude Code 每次新会话先读这里。
> 状态：✅ 已完成 / 🚧 进行中 / ⬜ 未开始 / ⚠️ 有风险

## 当前阶段

**M0 脚手架**（对应 16 周排期 Week 1）

## 里程碑

| 里程碑 | 目标 | 状态 | 完成日期 |
| --- | --- | --- | --- |
| M0 脚手架 | Monorepo + 4 个中间件 compose + auth-center 能登录 | ⬜ | - |
| M1 交易闭环 | 下单→支付→库存→物流端到端跑通 | ⬜ | - |
| M2 AI 导购 | SSE 对话 + RAG + 降级兜底 | ⬜ | - |
| M3 大促压测 | 5000 QPS 不垮，P99 < 1.5s | ⬜ | - |
| M4 上线 | 灰度发布 + 监控告警 + 回滚演练 | ⬜ | - |

## 本周任务（Week 1）

- [ ] 初始化 Monorepo 目录骨架
- [ ] 父 pom.xml + 子模块注册
- [ ] `infra/compose/middleware.yml` 起 MySQL/Redis/RocketMQ/MinIO
- [ ] `services/auth-center` 手机号登录 + JWT 颁发
- [ ] auth-center 单测绿（Testcontainers）
- [ ] 全局异常处理器 + Result<T> 公共库抽到 `packages/common`

## 已完成

- 2026-09-30：仓库骨架初始化；CLAUDE.md / ADR-0001 / progress.md 落地。

## 进行中

- `auth-center` 登录接口（W1 任务）

## 待办池

- [ ] user-service（W2）
- [ ] file-service（W2）
- [ ] product-service（W3）
- [ ] cart-service（W3）
- [ ] order-service + 状态机（W4）
- [ ] inventory-service Redis Lua（W5）
- [ ] payment-service 沙箱 + e2e 脚本（W6）
- [ ] marketing-service（W7）
- [ ] logistics / aftersale（W8）
- [ ] ai-orchestrator（W9-10）
- [ ] BFF + 前端（W11-12）
- [ ] 可观测接入（W13）
- [ ] 压测（W14）
- [ ] 灰度 + 回滚演练（W15）

## 风险与阻塞

（暂无）

## 变更记录

- 2026-09-30：项目初始化。
