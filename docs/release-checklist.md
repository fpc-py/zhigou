# 智购 · 发布检查清单

> 每次发布前逐项检查，完成后在 `[ ]` 中打 `[x]`。
> 发布负责人须在 CHANGELOG 中记录本次变更摘要。

---

## 一、发布前准备

### 1.1 代码冻结

- [ ] 所有待发布 feature 分支已合入 `develop`
- [ ] `develop` 分支 CI 全量测试通过
  ```bash
  ./mvnw -DskipTests=false clean verify  # Java 服务
  cd services/ai-orchestrator && pytest   # AI 服务
  cd apps/bff-shop && npm test            # BFF
  cd apps/h5-shop && npm run build        # H5 构建验证
  cd apps/admin-merchant && npm run build # 商家后台构建验证
  ```
- [ ] 无 P0/P1 级别的未关闭 Issue
- [ ] 数据库迁移脚本已 Review（Flyway `V<date>__<desc>.sql`）
- [ ] 与上下游团队确认 API 契约变更（OpenAPI / proto）

### 1.2 版本号

- [ ] 父 POM 版本号已更新（`pom.xml` `<version>`）
- [ ] BFF `package.json` version 已更新
- [ ] H5 `package.json` version 已更新
- [ ] AI Orchestrator `__version__`（如有）已更新
- [ ] Git tag 已打：`git tag v{major}.{minor}.{patch}`
- [ ] CHANGELOG.md 已更新

### 1.3 数据库

- [ ] Flyway 迁移脚本在 staging 环境执行通过
- [ ] 回滚脚本已准备（如有破坏性变更）
- [ ] 大表索引变更已评估执行时间
- [ ] 数据备份已触发
  ```bash
  mysqldump -h $DB_HOST -u $DB_USER -p$DB_PASS zhigou > backup/release-$(date +%Y%m%d).sql
  ```

### 1.4 配置

- [ ] Nacos 配置中心已同步最新配置（或本地 `application-{env}.yml`）
- [ ] 密钥已更新（非 `.env.example` 中的占位值）
  - `JWT_SECRET` — 生产环境使用新生成的 256 位随机字符串
  - 数据库密码 — 已轮换
  - 第三方 API key — 已确认有效期
- [ ] 日志级别确认：生产环境 `INFO`，非 `DEBUG`
- [ ] 降级配置已 review：`config/fallback.yml`（`ai.enabled`、`llm.timeout_ms`）

---

## 二、构建与部署

### 2.1 构建

- [ ] Java 服务构建
  ```bash
  ./mvnw -DskipTests clean package -Pproduction
  ```
- [ ] Docker 镜像构建并推送
  ```bash
  for svc in auth-center user-service product-service cart-service order-service \
            inventory-service payment-service marketing-service logistics-service \
            aftersale-service file-service; do
    docker build -t zhigou/$svc:$(git rev-parse --short HEAD) -f services/$svc/Dockerfile services/$svc
    docker push zhigou/$svc:$(git rev-parse --short HEAD)
  done
  ```
- [ ] BFF 构建
  ```bash
  cd apps/bff-shop && npm ci && npm run build
  docker build -t zhigou/bff-shop:$(git rev-parse --short HEAD) .
  docker push zhigou/bff-shop:$(git rev-parse --short HEAD)
  ```
- [ ] H5 构建
  ```bash
  cd apps/h5-shop && npm ci && npm run build
  # 产物在 dist/，部署到 CDN / Nginx
  ```
- [ ] 商家后台构建
  ```bash
  cd apps/admin-merchant && npm ci && npm run build
  ```
- [ ] AI Orchestrator 构建
  ```bash
  cd ai-services/ai-orchestrator && docker build -t zhigou/ai-orchestrator:$(git rev-parse --short HEAD) .
  docker push zhigou/ai-orchestrator:$(git rev-parse --short HEAD)
  ```
- [ ] 镜像 tag 使用 git SHA（**禁止**使用 `latest`）

### 2.2 部署（K8s）

- [ ] K8s manifest 已更新镜像版本
  ```bash
  kustomize edit set image zhigou/auth-center=zhigou/auth-center:$(git rev-parse --short HEAD)
  # 对所有服务重复
  ```
- [ ] ConfigMap / Secret 已更新
- [ ] 灰度发布配置已就绪（`kind: Canary` 或 `Rollout`）
- [ ] 手工触发部署
  ```bash
  kubectl apply -k infra/helm/overlays/production
  ```

### 2.3 部署（Docker Compose）

- [ ] `.env` 文件已更新
- [ ] 拉取最新镜像
  ```bash
  docker compose -f infra/compose/middleware.yml pull
  ```
- [ ] 启动服务
  ```bash
  docker compose -f infra/compose/middleware.yml up -d
  ```

---

## 三、发布中检查

### 3.1 启动顺序

```
顺序                    依赖                   健康检查端点
─────────────────────────────────────────────────────────────
1. MySQL / Redis /      —                     mysqladmin ping
   RocketMQ / MinIO /                         redis-cli PING
   PostgreSQL                                  pg_isready
2. auth-center          MySQL + Redis          GET /actuator/health
3. user-service         MySQL + Redis          GET /actuator/health
4. product-service      MySQL + Redis + auth   GET /actuator/health
5. inventory-service    MySQL + Redis + auth   GET /actuator/health
6. marketing-service    MySQL + auth           GET /actuator/health
7. order-service        MySQL + RocketMQ       GET /actuator/health
8. payment-service      MySQL + RocketMQ       GET /actuator/health
9. logistics-service    MySQL + auth           GET /actuator/health
10. aftersale-service   MySQL + RocketMQ       GET /actuator/health
11. file-service        MySQL + MinIO + auth   GET /actuator/health
12. ai-orchestrator     PostgreSQL + MQ        GET /health
13. BFF                 auth + product + …     GET /health
14. H5 / Admin CDN     BFF                     HTTP 200
```

- [ ] 前序服务健康检查通过后启动后续服务
- [ ] 每个服务的 `/actuator/health/readiness` 返回 200

### 3.2 监控确认

- [ ] Prometheus target 全部 UP
  ```bash
  curl http://prometheus:9090/api/v1/targets | jq '.data.activeTargets[] | {job, health}'
  ```
- [ ] Grafana 面板数据正常（RED / JVM / 业务指标）
- [ ] 日志已接入 Loki，按 `traceId` 可检索
- [ ] 告警规则已生效（P1/P2/P3）
  - P1: 服务 down、5min 错误率 > 5%、支付成功率 < 99%
  - P2: P95 > 2s、DB 连接池耗尽、MQ 积压 > 1000
  - P3: 磁盘 > 80%、JVM 堆 > 85%

---

## 四、发布后验证

### 4.1 冒烟测试

- [ ] 用户注册 / 登录链路
  ```bash
  curl -X POST http://bff-host/auth/send-sms-code -d '{"phone":"13800000000"}'
  curl -X POST http://bff-host/auth/login -d '{"phone":"13800000000","code":"123456"}'
  ```
- [ ] 首页加载
  ```bash
  curl -H "Authorization: Bearer $TOKEN" http://bff-host/home/feed
  ```
- [ ] 商品浏览 → 加购 → 下单 → 支付
  ```bash
  # e2e 脚本
  bash scripts/e2e-order.sh
  ```
- [ ] AI 对话
  ```bash
  curl -N -X POST http://bff-host/chat/sse \
    -H "Authorization: Bearer $TOKEN" \
    -d '{"query":"推荐跑步鞋","userId":"u1001","sessionId":"s001"}'
  ```
- [ ] 商家后台登录
  ```bash
  curl -X POST http://admin-host/login -d '{"username":"admin","password":"admin123"}'
  ```
- [ ] 管理端商品上下架
- [ ] 管理端售后审核
- [ ] 数据看板可访问

### 4.2 回归测试

- [ ] 核心接口 P95 < 1s（对比上次发布数据）
- [ ] 错误率 < 0.5%
- [ ] 全链路压测通过
  ```bash
  k6 run scripts/load-test/smoke-test.js --vus 100 --duration 60s
  ```

### 4.3 数据校验

- [ ] 订单数据正确落库
- [ ] 库存扣减一致（无超卖）
- [ ] 支付回调正常
- [ ] 向量数据正确写入 pgvector

---

## 五、回滚方案

### 5.1 回滚条件

出现以下任一条件立即回滚：
- 核心接口错误率 > 5%（连续 5 分钟）
- 支付成功率 < 99%
- 数据库写入不一致（订单、库存、支付）
- P95 延迟 > 2s 且持续上升

### 5.2 回滚步骤

```bash
# 1. K8s 回滚到上一版本
kubectl rollout undo deployment/<svc>

# 2. Docker Compose 回滚
docker compose -f infra/compose/middleware.yml down <svc>
docker compose -f infra/compose/middleware.yml up -d <svc>

# 3. 数据库回滚（Flyway）
# 如果本次发布含破坏性迁移，执行回滚脚本
bash scripts/rollback-db.sh v{previous-version}

# 4. 验证回滚后状态
# 见第四节「发布后验证」

# 5. 通知相关方
# 在发布群通知：回滚完成、原因、预计修复时间
```

### 5.3 回滚后

- [ ] 创建 Hotfix 分支从 `main` 切出
- [ ] 修复问题
- [ ] 提交 PR 合入 `main` 和 `develop`
- [ ] 重新触发发布流程

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