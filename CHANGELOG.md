# 智购 · 变更记录（CHANGELOG）

> 每个可交付单元（功能/修复/重构/文档）在此登记，格式遵循 [Keep a Changelog](https://keepachangelog.com/zh-CN/1.1.0/) 与 [语义化版本](https://semver.org/lang/zh-CN/)。
> 格式：`[类型] 模块：描述`。类型：feat / fix / refactor / test / docs / chore。

## [Unreleased]

### P0 · M1 收尾（进行中）
- RocketMQ 事务消息最终一致性（outbox 投递任务）
- 超时关单 / 支付对账 / 退款资金流
- 售后逆向全流程
- 营销活动（满减/秒杀/拼团/凑单）
- 评价增量向量更新
- 用户画像 / 收藏 / 浏览历史
- Java 服务 JWT 过滤器、日志脱敏、Dockerfile、CI/CD

## [0.1.0] - 2026-10-07

### feat
- **前端（apps/h5-shop）**：16 条路由 + 5 TabBar；8 屏对齐原型——首页（问候头部/快捷宫格/真实商品流）、AI 对话（气泡 + 工具理由 + 商品卡）、商品详情（AI 摘要/规格网格/比价入口）、AI 比价（最优方案 + 渠道演示表）、AR 试穿/智能衣橱/社区（视觉占位）、我的（真实资料 + 订单栏 + AI 模型卡）
- **交易闭环**：购物车（服务端数据/勾选/数量/清空）、结算（地址/优惠券/运费/优惠试算/提交幂等/沙箱支付）、订单列表（状态筛选/取消/去支付）、订单详情、地址管理（CRUD）、优惠券页
- **BFF（apps/bff-shop）**：新增 cart/order/payment/marketing/user/logistics 六个透传模块；product 补 `GET /product/page` 透传；service.config 补齐 11 个微服务地址
- **order-service**：新增 `GET /order/mine?userId=`；下单前调 product-service 拉取真实价格与 SPU 名称写入订单（失败回退占位不阻断）；create/cancel 优先读 `x-user-id` header
- **cart-service**：新增 `DELETE /cart/{skuId}`（校验存在后删除 Redis 条目）

### fix
- **order-service**：订单金额/商品名由写死占位（100 分 / SKU-xxx）改为真实商品数据
- **cart-service**：购物车无法删除单条（前端曾用 count=0 残留条目）→ 真实删除接口打通

### docs
- 文档体系统一：新增 `docs/README.md` 文档中心；重写根 `README.md`、`CLAUDE.md`、`docs/progress.md`；新增 `CHANGELOG.md`、`CONTRIBUTING.md`、`apps/bff-shop/README.md`、`services/README.md`；重写 `apps/h5-shop/README.md`、`apps/admin-merchant/README.md`；修正 `docs/release-checklist.md` 路径错误；修正 `.claude/skills` 目录拼写

## [0.0.2] - 2026-10-02

### feat
- BFF 层压测（50 并发 / 4500 请求 / 0 错误 / P95 131ms / RPS 718），报告见 `docs/load-test/`
- Mock 数据机制：Flyway profile 分离（`db/mock` 仅 dev 执行）+ 固定 ID 段 + 一键清除脚本 `scripts/mock-data/clean-mock.ps1`
- 生产就绪度评估（就绪度 30%，2026-10-07 已并入 `docs/智购开发任务差距分析报告.md` 附录 A）

## [0.0.1] - 2026-09-30

### feat
- M0 脚手架：Monorepo + 父 POM + 12 个服务骨架
- 中间件 Docker Compose（MySQL / Redis / RocketMQ / MinIO / PostgreSQL + pgvector）
- auth-center：手机号验证码登录 + JWT 签发（HS256，access 2h / refresh 14d）
- 公共库 `packages/common`：Result<T>、全局异常、BizException
- 11 个 Java 服务 Flyway 初始化迁移 + Testcontainers 集成测试骨架
- ai-orchestrator：LangChain/LangGraph 对话 Agent + SSE + RAG（pgvector）+ 降级开关 `config/fallback.yml`
- 可观测：Prometheus / Grafana / Loki 配置 + RED/JVM/业务指标面板
- BFF（NestJS）：JWT 鉴权、home/product/chat 聚合、SSE 透传
- H5 脚手架 + admin-merchant 脚手架

### docs
- CLAUDE.md、ADR-0001（记录架构决策）、progress.md、README.md 初版
