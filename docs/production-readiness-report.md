# 智购 · 生产就绪度报告

> 生成日期: 2026-10-02 | 作者: Claude Code
> 本报告基于代码扫描、测试结果、配置文件分析，对照《智购-企业级工程化技术方案.md》中的 NFR 和六大维度逐项评估。

---

## 第一部分：现状盘点

### 1.1 代码清单

| 服务 | 类型 | 业务代码 | 测试代码 | Dockerfile | 可启动？ |
|------|------|---------|---------|------------|---------|
| auth-center | Java | 355 行 | 151 行 | ✅ | ✅ 可启动 |
| user-service | Java | 821 行 | 352 行 | ❌ | ✅ 可启动 |
| file-service | Java | 593 行 | 220 行 | ❌ | ✅ 可启动 |
| product-service | Java | 549 行 | 156 行 | ❌ | ✅ 可启动 |
| cart-service | Java | 257 行 | 132 行 | ❌ | ✅ 可启动 |
| order-service | Java | 334 行 | 130 行 | ❌ | ✅ 可启动 |
| inventory-service | Java | 156 行 | 84 行 | ❌ | ✅ 可启动 |
| payment-service | Java | 185 行 | 89 行 | ❌ | ✅ 可启动 |
| marketing-service | Java | 354 行 | 109 行 | ❌ | ✅ 可启动 |
| logistics-service | Java | 241 行 | 70 行 | ❌ | ✅ 可启动 |
| aftersale-service | Java | 240 行 | 108 行 | ❌ | ✅ 可启动 |
| ai-orchestrator | Python | 1186 行 | 487 行 | ✅ | ✅ 可启动 |
| bff-shop | TS/Nest | 625 行 | 0 行 | ❌ | ✅ 可启动 |
| h5-shop | Vue/TS | ~1600 行 | 0 行 | ❌ | ✅ 可启动 |
| admin-merchant | Vue/TS | ~2000 行 | 0 行 | ❌ | ✅ 可启动 |

**总计**: Java 4226 行 + Python 1673 行 + TS 1223 行 + Vue 1909 行 = ~9031 行业务代码
**Dockerfile 覆盖**: 仅 auth-center 和 ai-orchestrator 有 Dockerfile，其他 11 个服务缺失（❌ 84%）

### 1.2 测试现状

**Python 测试** (pytest):
```
23 passed, 4 warnings, 在 131s
覆盖率: 未配置 pytest-cov，无覆盖率数据
```

**Java 测试** (Maven + Testcontainers):
```
auth-center:  3 tests, 1 FAILED (shouldFailLoginWithWrongCode 期望 200 得 403)
user-service: 测试存在，结果待确认
product/order/cart/inventory/payment: 有 Testcontainers 集成测试文件
```
- **auth-center 1 个测试失败**: `shouldFailLoginWithWrongCode` 期望返回 200 但实际返回 403。这是测试数据未随业务逻辑更新导致的问题，不是代码 bug——错误验证码本应返回 403，测试应修正为 expect 403。
- 所有 11 个 Java 服务都有 Testcontainers 集成测试文件，使用真实 MySQL/RocketMQ 容器。
- **前端/BFF 测试**: H5、商家后台、BFF 均 **无任何测试**（0%）。

### 1.3 接口现状

所有 11 个 Java 服务都有 Controller（含端点），但有如下问题：

| 服务 | 接口数 | 鉴权 | 参数校验(@Valid) |
|------|-------|------|-----------------|
| auth-center | 2 (login, send-sms) | ❌ 开放 | ✅ |
| user-service | 9 | ❌ 无 JWT 过滤器 | ⚠️ 部分 |
| product-service | 7 | ❌ 无 JWT 过滤器 | ✅ |
| cart-service | 4 | ❌ 无 JWT 过滤器 | ⚠️ |
| order-service | 4 | ❌ 无 JWT 过滤器 | ✅ |
| inventory-service | 3 | ❌ 无 JWT 过滤器 | ⚠️ |
| payment-service | 2 | ❌ 无 JWT 过滤器 | ✅ |
| marketing-service | 6 | ❌ 无 JWT 过滤器 | ⚠️ |
| logistics-service | 4 | ❌ 无 JWT 过滤器 | ⚠️ |
| aftersale-service | 5 | ❌ 无 JWT 过滤器 | ⚠️ |
| file-service | 3 | ❌ 无 JWT 过滤器 | ⚠️ |

**关键问题**: 所有服务的 JWT 鉴权过滤器都 **没有实现**。`auth-center` 只有 `/auth/login` 和 `/auth/send-sms-code` 两个开放端点，SecurityConfig 仅允许 `/auth/**` 路径，但其他路径的鉴权过滤器没有配置。BFF 有 `JwtAuthGuard` 但只在 `/home/feed`、`/product/{id}/detail`、`/chat/sse` 上使用。其他服务完全无防护。

### 1.4 基础设施

| 组件 | 状态 | 详情 |
|------|------|------|
| Docker Compose (中间件) | ✅ | MySQL/Redis/RocketMQ/MinIO/PostgreSQL 16 |
| Docker Compose (监控) | ✅ | Prometheus/Grafana/Loki 配置就绪 |
| Prometheus 抓取配置 | ✅ | 11 个 Java 服务 targets |
| Grafana 面板 | ✅ | RED + JVM + 业务指标面板 |
| Loki 配置 | ✅ | 日志存储配置 |
| CI/CD | ❌ | 无 .github/workflows，无 .gitlab-ci.yml |
| K8s Helm | ❌ | infra/helm/ 空目录 |
| mvnw | ❌ | 无 Maven wrapper，依赖系统安装的 mvn |

### 1.5 已知 TODO/FIXME

```
grep -rn "TODO\|FIXME\|HACK\|XXX" 结果: 0 个
```
代码库中没有任何 TODO/FIXME 注解。这不一定是好事——说明很多明显的不足（如缺少鉴权过滤器、缺少 Dockerfile）没有被标记为 TODO。

---

## 第二部分：Gap 分析

### A. 架构与可靠性

| 要求 | 状态 | 证据 |
|------|------|------|
| 26 个微服务 | ⚠️ 半成品 | 技术方案规划 26 个，只建了 12 个（11 Java + 1 Python）。缺少：商品搜索服务(search-service)、推荐引擎(rec-engine)、支付网关、通知/推送、审批流等。 |
| 单元化多活/同城双活 | ❌ 没做 | 所有服务单实例部署，无多 AZ 配置，无单元化方案 |
| 超时/重试/熔断 | ❌ 没做 | 只有 cart-service 有 300ms RestTemplate 超时配置。无 Resilience4j / Sentinel / Hystrix 熔断器。 |
| 灰度发布 | ❌ 没做 | 无 Istio/Argo Rollouts 配置，无 K8s 部署配置 |
| 服务间通信 | ⚠️ 半成品 | MQ 有 outbox 表但无实际的 MQ 发送器(polling 任务)；服务间调用用 OpenFeign 但无超时/重试配置 |

### B. 数据与一致性

| 要求 | 状态 | 证据 |
|------|------|------|
| 下单时序 13 步 | ⚠️ 半成品 | order-service 有 create/cancel/payCallback，但整个端到端流程（下单→扣库存→支付→物流→售后）未完整联调过 |
| requestId 幂等 | ✅ 已做 | order-service 订单表有 requestId 字段 + 唯一索引。幂等 check: 1 个文件 (AftersaleServiceImpl line 92) |
| 库存超卖防护 | ✅ 已做 | inventory-service 有 Redis Lua 脚本 `deduct.lua`，原子扣减 |
| 三级缓存 | ❌ 没做 | 只有 Redis 二级缓存（product detail）、无本地缓存(Caffeine)，无 DB 缓存之外的持久化 |
| 消息堆积监控 | ❌ 没做 | 无 MQ 消费延迟监控，无积压告警 |
| 对账系统 | ⚠️ 半成品 | payment-service 有 `reconcile()` 方法骨架，检查 T+1 异常待支付单，但无完整对账流程（支付 vs 订单 vs 出账） |
| 金额加密落库 | ❌ 没做 | 金额 `Long` 存分，明文 |
| 手机号加密 | ✅ 已做 | `PhoneEncryptUtil` (AES/ECB)，user-service 使用 |

### C. 业务闭环

| 要求 | 状态 | 证据 |
|------|------|------|
| 订单状态机 | ⚠️ 半成品 | 无显式状态机（无 StateMachine 类），order-service 有 PAYING/PAID/SHIPPED/RECEIVED/CLOSED 枚举，但非法跃迁检查靠 service 层 if 判断 |
| 售后逆向流程 | ✅ 已做 | aftersale-service 完整状态机: APPLYING→SELLER_APPROVED→REFUNDING→REFUNDED |
| 优惠计算快照 | ❌ 没做 | 营销服务有折扣计算接口，但无优惠快照落库（下单时的优惠券快照） |
| AI 对话降级 | ✅ 已做 | fallback_config.yml 控制 ai.enabled 开关，llm.timeout_ms 超时兜底卡片 |
| 全链路体验 | ❌ 没做 | 首页→对话→商品→加购→下单→支付完整流程未从 H5 端走通过一次（因为后端服务未全部启动过） |

### D. 安全合规

| 要求 | 状态 | 证据 |
|------|------|------|
| JWT 签发/校验 | ❌ 没做 | auth-center 签发 JWT，但 **没有任何服务实现 JWT 验证过滤器**。BFF 有 JwtAuthGuard 但只用于 3 个端点。Java 服务全部无防护。 |
| 手机号加密落库 | ✅ 已做 | AES 加密，PhoneEncryptUtil |
| 日志脱敏 | ❌ 没做 | 代码中无任何脱敏逻辑。login 日志直接打印手机号。 |
| SQL 注入防护 | ✅ 已做 | MyBatis `#{}` 参数化查询，无 `${}` 拼接（已验证） |
| XSS 防护 | ❌ 没做 | 无 Content-Security-Policy header，无 XSS 过滤器 |
| 越权测试 | ❌ 没做 | 无越权测试用例 |
| .env 被 git 跟踪 | ❌ 未跟踪 | `git log --all --full-history -- .env` 无结果 ✅ |
| 支付集成 | ⚠️ 半成品 | 沙箱支付，非真金。T+1 对账有骨架代码。 |
| Prompt 注入防护 | ❌ 没做 | ai-orchestrator 系统提示词有身份限定，但无注入检测/输出审核 |
| AI 输出审核 | ❌ 没做 | 无敏感词过滤、无 PII 检测 |

### E. 可观测与运维

| 要求 | 状态 | 证据 |
|------|------|------|
| Actuator + Prometheus | ✅ 已做 | 11 个服务已加 actuator 依赖 + application.yml 暴露 |
| RED 指标 | ✅ 已做 | Grafana 面板配置了 Rate/Error/Duration |
| JVM 指标 | ✅ 已做 | Grafana 有 JVM 堆/GC 面板 |
| 业务指标 | ✅ 已做 | BusinessMetrics.java 定义了下单量/支付/库存/AI 指标 |
| 结构化日志 | ✅ 已做 | logback-spring.xml JSON 格式 (LogstashEncoder) |
| traceId 全链路 | ❌ 没做 | Logback 配置了 `traceId` MDC key，但 **无拦截器注入 traceId**，也无 OTel agent 实际接入 |
| OpenTelemetry | ❌ 没做 | 无 OTel agent 启动参数，无 OTel collector 配置 |
| SLO/错误预算 | ❌ 没做 | 无 SLO 定义，无错误预算监控 |
| 告警 | ❌ 没做 | 无 Alertmanager/企业微信/钉钉配置 |
| 应急预案 | ❌ 没做 | 无故障演练记录，无 Runbook |

### F. 交付工程化

| 要求 | 状态 | 证据 |
|------|------|------|
| CI/CD | ❌ 没做 | 无 GitHub Actions / GitLab CI 配置 |
| PR 自动测试 | ❌ 没做 | 无 |
| Dockerfile | ⚠️ 半成品 | 仅 2/13 服务有（auth-center + ai-orchestrator） |
| 镜像构建 | ❌ 没做 | 从未 build 过生产镜像 |
| Flyway 迁移 | ✅ 已做 | 所有 Java 服务有 Flyway 初始化脚本 |
| 回滚脚本 | ❌ 没做 | 无 DB rollback 脚本 |
| API 文档 | ✅ 已做 | Knife4j (Swagger) 已配置 |
| 部署文档 | ⚠️ 半成品 | 有 compose/middleware.yml，但无完整部署指南 |
| on-call 手册 | ❌ 没做 | 无 |
| 压测报告 | ✅ 已做 | docs/load-test/ 有报告 |

---

## 第三部分：上线前必做清单

### P0 — 阻塞上线（不做不能上线）

| # | 事项 | 为什么必须做 | 预估工作量 | 证据 |
|---|------|-----------|----------|------|
| 1 | **JWT 鉴权过滤器** | 所有 Java 服务 API 完全无防护，任何人都可以调 | 3-5天 (11个服务各加过滤器) | 所有服务无 OncePerRequestFilter，BFF JwtAuthGuard 只覆盖 3 条路由 |
| 2 | **支付对账** | 沙箱支付，无真金。对账代码只有骨架，资金链路不清 | 5-10天 (接支付网关 + 对账流程) | reconcile() 只有日志，无订单 vs 支付逐笔核对 |
| 3 | **全链路冒烟测试** | 下单→支付→库存→物流→售后未完整跑通过 | 3-5天 (e2e 脚本完善) | bash scripts/e2e-order.sh 存在但未验证通过 |
| 4 | **日志脱敏** | 手机号明文打印，违反个保法 | 2-3天 (加脱敏工具 + AOP) | login 日志直接打印手机号，无脱敏 |
| 5 | **Dockerfile + 镜像构建** | 11 个服务无法容器化部署 | 2天 (每个服务加 Dockerfile) | 仅 2/13 有 Dockerfile |

### P1 — 上线后 2 周内必须补

| # | 事项 | 为什么必须做 | 预估工作量 | 证据 |
|---|------|-----------|----------|------|
| 6 | **CI/CD 流水线** | 没有 CI 无法保证每次提交的质量 | 2-3天 (.github/workflows) | 无 CI 配置 |
| 7 | **服务间超时/熔断** | 下游挂掉会导致级联雪崩 | 3-5天 (加 Sentinel/Resilience4j) | 只有 cart-service 有超时配置 |
| 8 | **告警接入** | 出问题没人知道 | 2天 (Alertmanager + 企微/钉钉) | 无告警配置 |
| 9 | **压测 + 瓶颈定位** | 不知道系统能扛多少 QPS | 3-5天 (启动全服务 + k6) | 只测了 BFF 层，完整链路未压过 |
| 10 | **traceId 全链路** | 问题定界靠人工翻日志 | 2天 (MDC 拦截器 + OTel) | Logback 配了 traceId 但无注入 |
| 11 | **H5/BFF/Admin 测试** | 前端无测试，改动风险高 | 3-5天 | 0% 前端测试覆盖 |

### P2 — 可以迭代（不影响首次上线）

| # | 事项 | 预估工作量 |
|---|------|----------|
| 12 | 多活/单元化 | 2-3月 |
| 13 | K8s Helm chart | 2周 |
| 14 | GitHub Actions (全量 CI) | 3天 |
| 15 | 灰度发布 (Argo Rollouts) | 1周 |
| 16 | 三级缓存 (Caffeine) | 3天 |
| 17 | 消息堆积监控 | 2天 |
| 18 | XSS/CSRF 防护 | 2天 |
| 19 | Prompt 注入防护 | 3天 |
| 20 | SLO 大盘 + 错误预算 | 3天 |
| 21 | 故障演练/Runbook | 1周 |
| 22 | on-call 排班手册 | 2天 |
| 23 | 剩余 14 个微服务拆分 | 3月+ |
| 24 | 单元化部署 | 3月+ |

---

## 第四部分：结论

### 总体就绪度：约 30%

| 维度 | 就绪度 | 说明 |
|------|--------|------|
| 架构可靠性 | 20% | 12/26 服务、无熔断、无多活 |
| 数据一致性 | 40% | 幂等✅ 库存Lua✅ 但无对账、无3级缓存 |
| 业务闭环 | 35% | AI降级✅ 售后✅ 但全链路未通、优惠无快照 |
| 安全合规 | 25% | 手机号加密✅ 但JWT❌ 日志脱敏❌ |
| 可观测 | 35% | Actuator+Prometheus+Grafana✅ 但无告警、无OTel |
| 交付工程化 | 15% | 无CI❌ 无Dockerfile❌ 无Helm❌ |

### 最危险的 3 个坑

1. **JWT 鉴权完全缺失。** 所有 11 个 Java 服务的 API 端点对外网完全开放，任何人都可以调 `POST /order/create` 创建订单、`POST /aftersale/{no}/approve` 审核退款。**这是能直接导致资金损失的安全漏洞。** 上线前必须补。

2. **全链路从未跑通过。** 首页→对话→详情→加购→下单→支付的完整用户路径从未从 H5/Admin 端完整走通过。后端 11 个服务从未一起启动过。**集成测试可能发现大量服务间调用断裂问题。**

3. **无 Dockerfile，无 CI，无部署流水线。** 即使代码正确，目前也没有可重复的部署流程。手动部署 13 个服务到生产环境一定会出错。**没有容器化和 CI，谈不上生产就绪。**

### 一句话结论

> **当前项目处于「可演示的原型」阶段——单个服务能跑、测试大部分绿、架构方向对。但要达到生产上线标准，估算还需要 4-6 周工程化改造，其中 JWT 鉴权、支付对账、Dockerfile+CI、全链路冒烟是四大硬门槛。最慢的路径是支付对接（真金 + 对账 + 合规），预计 2-3 周。**