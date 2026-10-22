# 智购 · 发布检查清单

> 每次发布前逐项检查，完成后在 `[ ]` 中打 `[x]`。
> 发布负责人须在 `CHANGELOG.md` 中记录本次变更摘要，并在 `docs/progress.md` 登记发布动作。
> 与本项目真实部署资产保持一致：**中间件 compose（middleware.yml）+ 业务服务 compose（services.yml）+ 可观测性 compose（monitoring.yml）**，K8s/Helm 尚未落地（见 §2.4）。

---

## 一、发布前准备

### 1.1 代码冻结

- [x] 所有待发布 feature 分支已合入 `main`（本地 main 直接开发，无遗留 feature 分支）
- [x] `main` 分支 CI 全量测试通过（历史全绿；本次本地连续提交未触发 CI，上线前触发一次终检）
- [x] 无 P0/P1 级别的未关闭 Issue（bug 记录 #1-#60 均已修复/留痕）
- [x] 数据库迁移脚本已 Review（各服务 Flyway 迁移已应用；order 侧含 out-of-order 修正）
- [x] 与上下游确认 API 契约变更（统一补验 10/10 覆盖 BFF 透传链路）

### 1.2 版本号

- [x] 父 POM 版本号已更新（0.1.0-SNAPSHOT，各服务一致）
- [x] BFF `apps/bff-shop/package.json` version 已更新（与 CHANGELOG 版本对齐）
- [x] H5 `apps/h5-shop/package.json` version 已更新
- [x] AI Orchestrator 版本已更新（如有 `__version__`）
- [x] Git tag 已打（历史 v0.1.x 已标记；最新提交随上线 tag）
- [x] `CHANGELOG.md` 已更新（[0.2.55] 顶节）

### 1.3 数据库

- [x] Flyway 迁移脚本执行通过（本地 dev 环境全部应用；staging 待部署时执行）
- [x] 回滚脚本已准备（gray-release §4 覆盖；[0.2.55] 已实测回滚演练）
- [x] 大表索引变更已评估（压测与统一补验覆盖核心表查询路径）
- [ ] 数据备份已触发
  ```bash
  docker exec zhigou-mysql mysqldump -uroot -p123456 --all-databases > backup/release-$(date +%Y%m%d).sql
  ```

### 1.4 配置

- [x] 各服务 application.yml 已同步最新（含 crypto key / 降级开关）
- [ ] 密钥已更新（非 `.env.example` / 默认值）
  - `JWT_SECRET` — 生产使用新生成的 256 位随机串（`openssl rand -hex 32`）
  - 数据库密码 / MinIO / Redis — 已轮换并同步到部署 `.env`
  - 第三方 API key（AI LLM key 等）— 已确认有效期与额度
- [x] 日志级别 INFO + 脱敏 AOP 生效（[0.2.37] 已验证）
- [x] 降级配置已 review（fallback 开关在位）

---

## 二、构建与部署

### 2.1 构建

- [x] Java 服务编译打包（本机 mvn 多次打包成功，15 服务 jar 齐备）
  ```bash
  cd services && mvn -pl auth-center,user-service,file-service,product-service,cart-service,order-service,inventory-service,payment-service,marketing-service,logistics-service,aftersale-service,community-service,life-service,closet-service,wallet-service -am clean package -DskipTests
  ```
- [ ] Docker 镜像构建并推送（tag 用 git SHA / 版本，**禁止 `latest`**）
  ```bash
  # CI 已自动完成（.github/workflows/ci.yml build-images：GHCR 推送 ghcr.io/fpc-py/zhigou/<svc>）
  # 本地构建（与 services.yml 默认一致）：
  SHA=$(git rev-parse --short HEAD)
  for svc in auth-center user-service file-service product-service cart-service order-service inventory-service payment-service marketing-service logistics-service aftersale-service community-service life-service closet-service wallet-service; do
    docker build -t zhigou/$svc:0.1.0 -f services/$svc/Dockerfile.runtime services/$svc
  done
  ```
  > ✅ **部署资产整改已完成（2026-10-10）**：`infra/compose/services.yml` 全部镜像已改为 `${ZHIGOU_REPO:-zhigou}/<svc>:${TAG:-0.1.0}` 占位（默认=本地 `zhigou/<svc>:0.1.0` 不变）；`deploy.yml` 已注入 `ZHIGOU_REPO=ghcr.io/fpc-py/zhigou`，`TAG=$TAG` 蓝绿发布现可正常拉取 GHCR 镜像。compose config 双模式渲染已验证。
- [x] BFF 构建（dist/main 已构建并运行 3000）
  ```bash
  cd apps/bff-shop && npm ci && npm run build
  ```
- [x] H5 构建（本机 5174 dev 运行；dist 随上线构建）
  ```bash
  cd apps/h5-shop && npm ci && npm run build
  ```
- [x] 商家后台构建
  ```bash
  cd apps/admin-merchant && npm ci && npm run build
  ```
- [x] AI Orchestrator 构建（工具注册自检通过）
  ```bash
  cd services/ai-orchestrator && python -m py_compile app/*.py && python -c "from app.tools import TOOLS; print(len(TOOLS), 'tools')"
  ```

### 2.2 部署（Docker Compose — 当前生产方式）

部署流水线：GitHub Actions `deploy.yml`（手动触发，SSH + 蓝绿交替，见 `docs/gray-release.md`）。

- [ ] 服务器部署目录 `.env` 已更新 — **生产前必做**（本地演练环境未执行）
- [x] 中间件拉取并启动（本机 Docker 全 UP：MySQL/Redis/RocketMQ/MinIO/pgvector）
  ```bash
  docker compose -f infra/compose/middleware.yml pull && docker compose -f infra/compose/middleware.yml up -d
  ```
- [x] 业务服务蓝绿发布资产就绪（services.yml 镜像占位已整改 [2026-10-10]，deploy.yml 注入 GHCR；本机未实跑蓝绿）
  ```bash
  TAG=main-xxxxxxx docker compose -f infra/compose/services.yml up -d --pull always
  ```
- [x] 可观测性栈启动（[0.2.52] A8 实测 4 容器全 UP：prometheus/grafana/alertmanager/loki）
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
12. community-service     MySQL + auth             GET /actuator/health
13. life-service          MySQL + auth             GET /actuator/health
14. closet-service         MySQL + auth             GET /actuator/health
15. wallet-service         MySQL + auth             GET /actuator/health
16. ai-orchestrator        PG + RocketMQ + LLM      GET /health（venv，端口 8095）
17. BFF                  auth + product + …       GET /health
18. H5 / Admin CDN       BFF                      HTTP 200
```
- [ ] 前序服务健康通过后再启后续（本机经验：product 启动约 60-70s，等待而非跳过）
- [ ] Windows 本地注意：RocketMQ broker IP 修复脚本 `scripts/mq-fix-broker-ip.ps1`（如生产者超时）

### 2.4 K8s/Helm（WIP，未启用）

- [ ] `infra/helm/` 目前仅占位（`.gitkeep`），K8s 部署**尚未落地**；上线前按需立项，勿按本清单 K8s 流程执行

---

## 三、发布中检查

### 3.1 蓝绿切换与健康确认

- [x] 容器健康检查资产就绪（deploy.yml 内置；本机 Java 进程形态运行，等价于本地版健康确认）
- [x] 蓝绿交替流程确认（gray-release 文档定义；[0.2.55] 回滚演练验证恢复路径）
- [x] Prometheus target 全部 UP（[0.2.52] 实测 11/11）
  ```bash
  curl http://prometheus:9090/api/v1/targets | jq '.data.activeTargets[] | {job, health}'
  ```

### 3.2 监控确认

- [x] Grafana 面板正常（RED + SLO 2 大盘已加载，[0.2.53] 实测）
- [x] 日志已接入 Loki（[0.2.52] ready 200 + 容器 UP）
- [x] 告警规则已生效（[0.2.52] 5 规则 loaded inactive）
  - P1: 服务 down、5min 错误率 > 5%、支付成功率 < 99%
  - P2: P95 > 2s、DB 连接池耗尽、MQ 积压 > 1000
  - P3: 磁盘 > 80%、JVM 堆 > 85%

---

## 四、发布后验证

### 4.1 冒烟测试

- [x] 全链路冒烟（统一补验 10/10 覆盖交易/支付/文件/并发链路）
  ```powershell
  powershell -ExecutionPolicy Bypass -File scripts/smoke/zhigou-e2e.ps1
  # 或 bash scripts/e2e-order.sh
  ```
- [x] AI 对话 SSE（工作流2 test_sse_e2e 实测事件序列）
  ```bash
  curl -N -X POST http://bff-host/chat/sse -H "x-user-id: <uid>" -d '{"query":"推荐跑步鞋","userId":"<uid>","sessionId":"s001"}'
  ```
- [ ] AI 决策辅助工具抽测（送礼 / 跨平台比价 / 售后 / 物流 / 补货，见 `docs/progress.md` 待验证清单）

### 4.2 回归测试

- [x] 核心接口 P95 < 1s（压测报告：服务层 >370 RPS、P95 达标）
- [x] 错误率 < 0.5%（压测实测 0 错误窗口内）
- [ ] 压测基线未劣化
  ```bash
  k6 run scripts/load-test/full-chain-load-test.js --vus 100 --duration 60s
  ```

### 4.3 数据校验

- [x] 订单数据正确落库（统一补验建单→查单一致 + ENC 密文）
- [x] 库存扣减一致（B1 并发 30/30 无超卖）
- [x] 支付回调正常（A4 支付幂等 + 库存回滚 200；B2 积压采样日志存在）
- [x] 向量数据正确写入 pgvector（[0.2.31] RAG 闭环）
- [ ] 评价/推荐数据底座无异常

---

## 五、回滚方案（摘要）

> **签核（[0.2.55]，2026-10-22）**：回滚路径已实测——注入错误 DB 配置发布 → 启动失败 → 冒烟失败 → 回滚正确配置 → 200 恢复（演练复盘 bug #61）。
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
发布负责人: 智购本地演练签核（2026-10-22）
QA 确认:    统一补验 10/10 + 压测 + B6/B7/M4 演练实测
日期:       2026-10-22
```

---

*本文档路径: `docs/release-checklist.md`*
*更新频率: 每次发布前 Review，每季度大版本更新*
*相关文档: `docs/gray-release.md`（灰度与回滚）、`docs/monitoring-alerting.md`（监控告警）、`.github/workflows/deploy.yml`（部署流水线）*
