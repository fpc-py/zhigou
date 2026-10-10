# 智购 · 监控告警与值班

> 回答"生产怎么知道出事了、怎么定级、谁处理"。
> 可观测性栈：Prometheus（抓取 11 服务 `/actuator/prometheus`）+ Grafana（RED 面板 `zhigou-red.json`）+ Loki（日志检索），编排见 `infra/compose/monitoring.yml`。

---

## 一、监控指标体系（RED + USE）

| 维度 | 指标 | PromQL 要点 | 来源 |
|---|---|---|---|
| Rate（流量） | QPS | `rate(http_server_requests_seconds_count[1m])` | Spring Actuator |
| Errors（错误） | 4xx/5xx 错误率 | `sum(rate(http_server_requests_seconds_count{status=~"4..|5.."}[1m])) / sum(rate(..._count[1m])) * 100` | Spring Actuator |
| Duration（延迟） | P50 / P95 / P99 | `histogram_quantile(0.50/0.95/0.99, sum(rate(http_server_requests_seconds_bucket[1m])) by (le, application))` | Spring Actuator |
| 资源 | JVM 堆 / GC | `jvm_memory_used_bytes{area="heap"}`、GC 计数 | Micrometer |
| 资源 | DB 连接池 | `hikaricp_connections_active` / `hikaricp_connections_pending` | HikariCP |
| 资源 | MQ 积压 | RocketMQ 消费延迟 / 积压数 | MQ 指标 |
| 业务 | 支付成功率 | 支付成功/失败计数比值（自建指标） | payment-service |
| 业务 | 订单/库存一致性 | outbox 未投递数、库存对账差异 | 自建指标 |

> 现有 Grafana 面板：`conf/grafana/dashboards/zhigou-red.json`（QPS / 错误率 / P50-P95-P99 / JVM 堆）。

---

## 二、SLO 与告警定级

### 2.1 目标（SLO，30 天滚动）

| 指标 | 目标 | 偏差容忍 |
|---|---|---|
| 可用性（核心接口） | 99.9% | 5xx < 0.1% |
| 支付成功率 | ≥ 99.5% | — |
| P95 延迟（核心接口） | < 1s | 5min 窗口 |
| 错误率 | < 0.5% | 5min 窗口 |

### 2.2 告警分级

| 级别 | 触发条件 | 响应时限 | 处理人 |
|---|---|---|---|
| **P1 紧急** | 服务 down、5min 错误率 > 5%、支付成功率 < 99%、数据不一致（订单/库存/支付） | 15 分钟 | 发布负责人 + 值班 |
| **P2 警告** | P95 > 2s 持续、DB 连接池耗尽、MQ 积压 > 1000、JVM 堆 > 85% | 1 小时 | 值班开发 |
| **P3 提示** | 磁盘 > 80%、非核心接口延迟上升、日志 ERROR 增多 | 1 个工作日 | 值班开发 |

### 2.3 告警规则示例（Prometheus `rules.yml`）

```yaml
groups:
  - name: zhigou-p1
    rules:
      - alert: ServiceDown
        expr: up == 0
        for: 2m
        labels: { severity: P1 }
        annotations: { summary: "{{ $labels.job }} 服务不可用" }
      - alert: HighErrorRate
        expr: sum(rate(http_server_requests_seconds_count{status=~"5.."}[5m])) / sum(rate(http_server_requests_seconds_count[5m])) > 0.05
        for: 5m
        labels: { severity: P1 }
        annotations: { summary: "5min 错误率超 5%" }
      - alert: PaymentFailure
        expr: payment_failed_total / (payment_success_total + payment_failed_total) > 0.01
        for: 5m
        labels: { severity: P1 }
  - name: zhigou-p2
    rules:
      - alert: HighLatency
        expr: histogram_quantile(0.95, sum(rate(http_server_requests_seconds_bucket[1m])) by (le, application)) > 2
        for: 5m
        labels: { severity: P2 }
      - alert: MQBacklog
        expr: rocketmq_consumer_accumulation > 1000
        for: 5m
        labels: { severity: P2 }
```

### 2.4 值班检查清单（告警触发后）

1. **确认影响面**：Grafana RED 面板看错误率/延迟波及哪些服务；Loki 按 `traceId` 检索失败请求日志
2. **初步定位**：看最近一次发布/变更（`CHANGELOG.md`、git log）；业务指标异常优先查 outbox/MQ/对账
3. **定级上报**：P1 立即通知发布负责人；P2/P3 按窗口处理
4. **处置**：按 `docs/gray-release.md` 判断回滚；配置问题改配置重启
5. **复盘**：记录到 `docs/测试问题bug记录.md` 与 `docs/progress.md`

---

## 三、日志检索

- 接入：Loki（`conf/loki/config.yml`）+ 服务 stdout 采集（compose logging driver / promtail）
- 检索：`{job="zhigou-<svc>"} |= "ERROR"`；按 `traceId` 串联全链路（BFF 透传 traceId 头）
- 脱敏：packages/common 日志脱敏 AOP 已生效（手机号/身份证/支付账号打码），Loki 中不落明文

---

## 四、压测基线（发布前必查）

- 报告：`docs/load-test/全链路压测报告.md`、`docs/load-test/results-20261002.json`
- 命令：`k6 run scripts/load-test/full-chain-load-test.js --vus 100 --duration 60s`
- 基线：见报告内 QPS / P95 / 错误率；发布后劣化 > 20% 视为回归

---

## 五、上线前可观测性自检

- [ ] Prometheus target 全部 UP（11 服务）
- [ ] Grafana RED 面板各图有数据（非空）
- [ ] 日志接入 Loki 且可按 traceId 检索
- [ ] 告警规则已加载（`rules.yml`）且 P1/P2 关键规则做过触发演练
- [ ] 值班群 / 通知渠道（邮件/IM webhook）已配置

---

*本文档路径: `docs/monitoring-alerting.md`*
*更新频率: 指标/告警/SLO 变更时*
*相关文档: `infra/compose/monitoring.yml`（编排）、`conf/prometheus/prometheus.yml`（抓取）、`conf/grafana/dashboards/zhigou-red.json`（面板）、`docs/release-checklist.md`（发布检查）*
