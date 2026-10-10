# 智购 · 灰度发布与回滚方案

> 回答"怎么把新版本安全放量到生产、出了问题怎么快速回来"。
> 当前生产方式为 **Docker Compose 蓝绿交替**（GitHub Actions `deploy.yml` 手动触发）；K8s/Helm 未落地前，本节即灰度发布的可执行方案。

---

## 一、发布策略总览

| 维度 | 策略 |
|---|---|
| 发布模式 | 蓝绿交替（services.yml 同一 compose 下，新镜像起健康后移除旧容器） |
| 灰度粒度 | 服务级分批：先边缘服务 → 再核心服务 → 最后 BFF/AI |
| 镜像标识 | `main-<git SHA>` 或 `v0.1.x`，**禁止 latest** |
| 回滚目标 | 上一已知良好镜像 tag（`git tag` 记录） |
| 决策门 | 每批放量后观察监控 10-15 分钟，满足 SLO 才继续 |

### 服务分批（按业务风险从低到高）

| 批次 | 服务 | 放量观察要点 |
|---|---|---|
| A 批（边缘） | file-service / marketing-service / logistics-service | 无核心链路回归；日志无 ERROR 激增 |
| B 批（核心） | product-service / inventory-service / cart-service / order-service / payment-service / aftersale-service | 交易链路冒烟（e2e-order）；库存/订单一致性 |
| C 批（入口） | auth-center / user-service / BFF / H5 / AI Orchestrator | 登录、SSE 对话、AI 工具抽测 |

---

## 二、灰度发布执行步骤

### 2.1 前置

- [ ] 通过 `docs/release-checklist.md` 发布前准备（§一）
- [ ] **部署资产整改已生效**（`services.yml` 镜像支持 `${TAG}` 注入，见 release-checklist §2.1 整改待办；未整改前 TAG 参数无效）
- [ ] 上一版本镜像 tag 已记录（`git tag` + GHCR 镜像保留）
- [ ] 监控可用（Prometheus/Grafana，`monitoring.yml`），重点指标已建阈值

### 2.2 分批放量

```bash
# 服务器部署目录（含 infra/compose 与 .env）
cd $DEPLOY_PATH

# A 批：边缘服务先行
TAG=v0.1.10 docker compose -f infra/compose/services.yml up -d --pull always file-service marketing-service logistics-service

# 观察 10-15 分钟：错误率 / P95 / 日志无异常后 → B 批
TAG=v0.1.10 docker compose -f infra/compose/services.yml up -d --pull always product-service inventory-service cart-service order-service payment-service aftersale-service

# 观察交易链路（bash scripts/e2e-order.sh）通过后 → C 批
TAG=v0.1.10 docker compose -f infra/compose/services.yml up -d --pull always auth-center user-service
# BFF / H5 / AI 随流水线一并发布，最后验证全链路
```

> 也可直接触发 GitHub Actions `deploy.yml`（workflow_dispatch 填 tag），其内置：拉镜像 → 起服务 → 健康检查 → 汇总。分批放量若需手动控制，按上表在服务器执行。

### 2.3 放量验证门（每批放量后必查）

| 指标 | 通过阈值 | 查询 |
|---|---|---|
| 服务健康 | 全部 `healthy` | `docker inspect --format '{{.State.Health.Status}}' zhigou-<svc>` |
| 错误率 | < 0.5%（5min） | Grafana RED 面板 / PromQL `sum(rate(http_server_requests_seconds_count{status=~"5.."}[5m]))` |
| P95 延迟 | < 1s（核心接口） | Grafana RED 面板 P95 曲线 |
| MQ 积压 | 无持续增长 | RocketMQ 控制台 / 监控面板 |
| 日志 | 无 ERROR 风暴 | Loki 检索 `{service="<svc>"} |= "ERROR"` |

### 2.4 全量确认

- [ ] 蓝绿切换完成（旧容器已移除，`docker compose ps` 只有新版本）
- [ ] 全链路冒烟通过（`scripts/smoke/zhigou-e2e.ps1` 17 步）
- [ ] 性能基准记录（release-checklist §六）
- [ ] 发布群通告：版本号、镜像 tag、放量批次、监控链接

---

## 三、回滚决策

### 3.1 立即回滚条件（任一触发）

- 核心接口错误率 > 5% 且连续 5 分钟
- 支付成功率 < 99%
- 数据库写入不一致（订单、库存、支付对账异常）
- P95 > 2s 且持续上升
- 数据损坏 / 安全事件（越权、数据泄露）

### 3.2 回滚类型判断

| 问题类型 | 回滚动作 |
|---|---|
| 仅代码回归（无 DB 破坏性迁移） | 镜像 tag 回退（最快，首选） |
| DB 含破坏性迁移且已执行 | 镜像回退 + DB 恢复（§4.3，高风险流程） |
| 纯配置问题 | 改配置重启，不回滚镜像 |

---

## 四、回滚执行

### 4.1 镜像回滚（首选）

```bash
cd $DEPLOY_PATH
# 回滚全部业务服务到上一良好 tag
TAG=<prev-tag> docker compose -f infra/compose/services.yml up -d --pull always
# 中间件不动（除非本次发布改了 middleware.yml）
```

### 4.2 验证回滚

- [ ] 健康检查全部通过（§2.3 第一行）
- [ ] 全链路冒烟通过
- [ ] 监控指标回落至正常区间
- [ ] 发布群通告：回滚完成、原因、预计修复时间

### 4.3 数据库回滚（仅破坏性迁移，高风险）

原则：
- **迁移向前兼容优先**：新代码能跑旧数据，就不需要 DB 回滚（推荐工程实践）
- 确需回滚时，流程如下：

```bash
# 1. 先停新版本业务服务（避免继续写坏数据）
docker compose -f infra/compose/services.yml stop <svc...>

# 2. 从发布前备份恢复（release-checklist §1.3 已要求备份）
docker exec -i zhigou-mysql mysql -uroot -p123456 zhigou < backup/release-<date>.sql

# 3. 旧版本服务以旧 schema 启动（Flyway 会跳过已执行迁移）
TAG=<prev-tag> docker compose -f infra/compose/services.yml up -d <svc...>

# 4. 数据一致性校验：订单 / 库存 / 支付对账
```

> ⚠️ DB 回滚期间**中断写流量**（或切只读），回滚完成后由 QA 全量校验数据一致性；此操作需发布负责人与 DBA 双人确认。

### 4.4 回滚后 Hotfix

- [ ] 从 `main` 切 Hotfix 分支
- [ ] 修复问题 → 提交 PR → 合入 `main`（含回归测试）
- [ ] 重新走灰度发布流程

---

## 五、演练与复盘

- [ ] 上线前完成一次「回滚演练」（模拟镜像回滚，验证命令与时限）
- [ ] 发布后 24h 内复盘：放量节奏、监控盲区、回滚耗时，记入 `docs/progress.md`

---

*本文档路径: `docs/gray-release.md`*
*更新频率: 发布流程/部署资产变更时*
*相关文档: `docs/release-checklist.md`（检查清单）、`docs/monitoring-alerting.md`（监控告警）、`.github/workflows/deploy.yml`（部署流水线）、`infra/compose/services.yml`（业务服务编排）*
