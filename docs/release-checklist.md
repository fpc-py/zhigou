# 智购 · 发布检查清单

> 每次发布前逐项检查，完成后在 `[ ]` 中打 `[x]`。
> 发布负责人须在 `CHANGELOG.md` 中记录本次变更摘要，并在 `docs/progress.md` 登记发布动作。
> 与本项目真实部署资产保持一致：**中间件 compose（middleware.yml）+ 业务服务 compose（services.yml）+ 可观测性 compose（monitoring.yml）**，K8s/Helm 尚未落地（见 §2.4）。

---

## 一、发布前准备

### 1.1 代码冻结

- [ ] 所有待发布 feature 分支已合入 `main`
- [ ] `main` 分支 CI 全量测试通过（GitHub Actions `ci.yml`，13+ 检查全绿）
- [ ] 无 P0/P1 级别的未关闭 Issue
- [ ] 数据库迁移脚本已 Review（Flyway `V<date>__<desc>.sql`，各服务 `src/main/resources/db/migration`）
- [ ] 与上下游确认 API 契约变更（OpenAPI / BFF 透传 / AI 工具入参）

### 1.2 版本号

- [ ] 父 POM 版本号已更新（`pom.xml` `<version>`）
- [ ] BFF `apps/bff-shop/package.json` version 已更新
- [ ] H5 `apps/h5-shop/package.json` version 已更新
- [ ] AI Orchestrator `services/ai-orchestrator/app/__init__.py` `__version__`（如有）已更新
- [ ] Git tag 已打：`git tag v{major}.{minor}.{patch}`
- [ ] `CHANGELOG.md` 已更新（顶部插入本次版本段）

### 1.3 数据库

- [ ] Flyway 迁移脚本在 staging 环境执行通过（`./mvnw -pl services/<svc> flyway:migrate -Dspring.profiles.active=staging`）
- [ ] 回滚脚本已准备（如含破坏性变更，见 `docs/gray-release.md` §4）
- [ ] 大表索引变更已评估执行时间（订单/库存/评价等核心表）
- [ ] 数据备份已触发
  ```bash
  docker exec zhigou-mysql mysqldump -uroot -p123456 --all-databases > backup/release-$(date +%Y%m%d).sql
  ```

### 1.4 配置

- [ ] 各服务 `application-{env}.yml`（**本项目未用 Nacos**，配置随镜像/部署目录分发）已同步最新
- [ ] 密钥已更新（非 `.env.example` / 默认值）
  - `JWT_SECRET` — 生产使用新生成的 256 位随机串（`openssl rand -hex 32`）
  - 数据库密码 / MinIO / Redis — 已轮换并同步到部署 `.env`
  - 第三方 API key（AI LLM key 等）— 已确认有效期与额度
- [ ] 日志级别：生产 `INFO`，非 `DEBUG`；脱敏 AOP（packages/common）已生效
- [ ] 降级配置已 review：`config/fallback.yml`（`ai.enabled`、`llm.timeout_ms`、比价/视觉降级开关）

---

## 二、构建与部署

### 2.1 构建

- [ ] Java 服务编译打包
  ```bash
  cd services && mvn -pl auth-center,user-service,file-service,product-service,cart-service,order-service,inventory-service,payment-service,marketing-service,logistics-service,aftersale-service -am clean package -DskipTests
  ```
- [ ] Docker 镜像构建并推送（tag 用 git SHA / 版本，**禁止 `latest`**）
  ```bash
  # CI 已自动完成（.github/workflows/ci.yml build-images：GHCR 推送 ghcr.io/fpc-py/zhigou/<svc>）
  # 本地构建（与 services.yml 默认一致）：
  SHA=$(git rev-parse --short HEAD)
  for svc in auth-center user-service file-service product-service cart-service order-service inventory-service payment-service marketing-service logistics-service aftersale-service; do
    docker build -t zhigou/$svc:0.1.0 -f services/$svc/Dockerfile.runtime services/$svc
  done
  ```
  > ✅ **部署资产整改已完成（2026-10-10）**：`infra/compose/services.yml` 全部镜像已改为 `${ZHIGOU_REPO:-zhigou}/<svc>:${TAG:-0.1.0}` 占位（默认=本地 `zhigou/<svc>:0.1.0` 不变）；`deploy.yml` 已注入 `ZHIGOU_REPO=ghcr.io/fpc-py/zhigou`，`TAG=$TAG` 蓝绿发布现可正常拉取 GHCR 镜像。compose config 双模式渲染已验证。
- [ ] BFF 构建
  ```bash
  cd apps/bff-shop && npm ci && npm run build
  ```
- [ ] H5 构建（产物 `dist/` 部署 CDN / Nginx）
  ```bash
  cd apps/h5-shop && npm ci && npm run build
  ```
- [ ] 商家后台构建
  ```bash
  cd apps/admin-merchant && npm ci && npm run build
  ```
- [ ] AI Orchestrator 构建（py_compile + 工具注册自检）
  ```bash
  cd services/ai-orchestrator && python -m py_compile app/*.py && python -c "from app.tools import TOOLS; print(len(TOOLS), 'tools')"
  ```

### 2.2 部署（Docker Compose — 当前生产方式）

部署流水线：GitHub Actions `deploy.yml`（手动触发，SSH + 蓝绿交替，见 `docs/gray-release.md`）。

- [ ] 服务器部署目录 `.env` 已更新（镜像仓库登录凭据、密钥）
- [ ] 中间件拉取并启动
  ```bash
  docker compose -f infra/compose/middleware.yml pull && docker compose -f infra/compose/middleware.yml up -d
  ```
- [ ] 业务服务蓝绿发布（镜像 tag 指定 `main-<SHA>` 或 `v0.1.x`）
  ```bash
  TAG=main-xxxxxxx docker compose -f infra/compose/services.yml up -d --pull always
  ```
- [ ] 可观测性栈启动
  ```bash
  docker compose -f infra/compose/monitoring.yml up -d
  ```

### 2.3 中间件就绪检查（启动顺序）

```
顺序                    依赖                      健康检查
──────────────────────────────────────────────────────────────
1. MySQL / Redis /       —                        mysqladmin ping / redis-cli PING
   RocketMQ / MinIO /
   PostgreSQL(pgvector)
2. auth-center           MySQL + Redis            GET /actuator/health
3. user-service          MySQL + Redis            GET /actuator/health
4. file-service          MySQL + MinIO            GET /actuator/health
5. product-service       MySQL + Redis + auth     GET /actuator/health
6. inventory-service     MySQL + Redis + auth     GET /actuator/health
7. marketing-service     MySQL + auth             GET /actuator/health
8. order-service         MySQL + RocketMQ         GET /actuator/health
9. payment-service       MySQL + RocketMQ         GET /actuator/health
10. logistics-service    MySQL + auth             GET /actuator/health
11. aftersale-service    MySQL + RocketMQ         GET /actuator/health
12. ai-orchestrator      PG + RocketMQ + LLM      GET /health
13. BFF                  auth + product + …       GET /health
14. H5 / Admin CDN       BFF                      HTTP 200
```
- [ ] 前序服务健康通过后再启后续（本机经验：product 启动约 60-70s，等待而非跳过）
- [ ] Windows 本地注意：RocketMQ broker IP 修复脚本 `scripts/mq-fix-broker-ip.ps1`（如生产者超时）

### 2.4 K8s/Helm（WIP，未启用）

- [ ] `infra/helm/` 目前仅占位（`.gitkeep`），K8s 部署**尚未落地**；上线前按需立项，勿按本清单 K8s 流程执行

---

## 三、发布中检查

### 3.1 蓝绿切换与健康确认

- [ ] 新服务容器 `docker inspect --format '{{.State.Health.Status}}'` 全部 `healthy`（deploy.yml 已内置 11 服务健康检查）
- [ ] 旧容器未清理前，流量不切换（蓝绿交替：新服务健康后移除旧容器）
- [ ] Prometheus target 全部 UP
  ```bash
  curl http://prometheus:9090/api/v1/targets | jq '.data.activeTargets[] | {job, health}'
  ```

### 3.2 监控确认

- [ ] Grafana 面板数据正常（`conf/grafana/dashboards/zhigou-red.json`：QPS / 错误率 / P50-P95-P99 / JVM 堆）
- [ ] 日志已接入 Loki（`infra/compose/monitoring.yml`），按 `traceId` 可检索
- [ ] 告警规则已生效（详见 `docs/monitoring-alerting.md`）
  - P1: 服务 down、5min 错误率 > 5%、支付成功率 < 99%
  - P2: P95 > 2s、DB 连接池耗尽、MQ 积压 > 1000
  - P3: 磁盘 > 80%、JVM 堆 > 85%

---

## 四、发布后验证

### 4.1 冒烟测试

- [ ] 全链路联调冒烟脚本（黄金路径 17 步）
  ```powershell
  powershell -ExecutionPolicy Bypass -File scripts/smoke/zhigou-e2e.ps1
  # 或 bash scripts/e2e-order.sh
  ```
- [ ] AI 对话 SSE（含工具调用）
  ```bash
  curl -N -X POST http://bff-host/chat/sse -H "x-user-id: <uid>" -d '{"query":"推荐跑步鞋","userId":"<uid>","sessionId":"s001"}'
  ```
- [ ] AI 决策辅助工具抽测（送礼 / 跨平台比价 / 售后 / 物流 / 补货，见 `docs/progress.md` 待验证清单）

### 4.2 回归测试

- [ ] 核心接口 P95 < 1s（对比上次发布数据，见 §六性能基准）
- [ ] 错误率 < 0.5%
- [ ] 压测基线未劣化
  ```bash
  k6 run scripts/load-test/full-chain-load-test.js --vus 100 --duration 60s
  ```

### 4.3 数据校验

- [ ] 订单数据正确落库（MySQL `zhigou_order`）
- [ ] 库存扣减一致（无超卖，Redis 预扣 + DB 对账）
- [ ] 支付回调正常（outbox + RocketMQ 投递无积压）
- [ ] 向量数据正确写入 pgvector（`zhigou_rag`）
- [ ] 评价/推荐数据底座无异常

---

## 五、回滚方案（摘要）

> 完整方案见 `docs/gray-release.md` §4「回滚」；出现以下任一条件立即回滚：
> - 核心接口错误率 > 5%（连续 5 分钟）
> - 支付成功率 < 99%
> - 数据库写入不一致（订单、库存、支付）
> - P95 延迟 > 2s 且持续上升

```bash
# 1. 业务服务回滚到上一镜像 tag
TAG=<prev-tag> docker compose -f infra/compose/services.yml up -d --pull always
# 2. 数据库回滚：破坏性迁移按 docs/gray-release.md §4.3 执行（Flyway + 备份恢复）
# 3. 验证回滚后状态（见 §四）
# 4. 发布群通知：回滚完成、原因、预计修复时间
```

---

## 六、性能基准

每次发布后记录以下数据，用于趋势分析：

| 指标 | 本次 | 上次 | 趋势 |
|------|------|------|------|
| 首页 P95 | — | — | — |
| 下单 P95 | — | — | — |
| 支付 P95 | — | — | — |
| 搜索 P95 | — | — | — |
| AI 对话 P95 | — | — | — |
| 整体错误率 | — | — | — |
| 整体 RPS | — | — | — |
| JVM 堆使用率 | — | — | — |
| GC 暂停时间 | — | — | — |

（压测基线参考：`docs/load-test/全链路压测报告.md`、`results-20261002.json`）

---

## 七、签署

```
发布负责人: __________________
QA 确认:    __________________
日期:       __________________
```

---

*本文档路径: `docs/release-checklist.md`*
*更新频率: 每次发布前 Review，每季度大版本更新*
*相关文档: `docs/gray-release.md`（灰度与回滚）、`docs/monitoring-alerting.md`（监控告警）、`.github/workflows/deploy.yml`（部署流水线）*
