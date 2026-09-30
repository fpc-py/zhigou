# 可观测性规范

> 此文件被 CLAUDE.md 引用。

## 日志

- 格式：结构化 JSON（Logback JSON encoder）
- 必须包含字段：`traceId`、`userId`、`requestId`（脱敏后）
- 级别使用原则：
  - `ERROR`：需要人工介入的问题（支付失败、DB 连接失败、MQ 投递失败）
  - `WARN`：业务异常但可降级（缓存未命中、第三方超时、限流触发）
  - `INFO`：关键节点（订单创建、支付成功、状态变更、登录）
  - `DEBUG`：只在 staging 环境开，生产禁止
- 错误日志必须带上下文（userId、订单号、请求参数摘要），不能只打 `e.getMessage()`

## 指标

- 每个服务暴露 `/actuator/prometheus`
- 必采指标：
  - **RED**：Rate（请求量）、Error（4xx/5xx 错误率）、Duration（P50/P95/P99）
  - **JVM**：堆内存、GC 次数/耗时、线程数
  - **业务**：下单量、支付成功率、库存扣减成功/失败、AI 对话完成率

## 链路追踪

- 所有 Java 服务通过 OTel JavaAgent 注入（启动参数 `-javaagent:opentelemetry-javaagent.jar`）
- 跨服务调用自动传递 traceId
- HTTP 调用：自动注入 `traceparent` header
- MQ 消息：在消息头中携带 traceId

## 告警

- 必须配置的告警规则：
  - P1：服务 down、5 分钟内错误率 > 5%、支付成功率 < 99%
  - P2：P95 延迟 > 2s、数据库连接池耗尽、MQ 消费积压 > 1000
  - P3：磁盘使用 > 80%、JVM 堆 > 85%

## 健康检查

- `/actuator/health`：存活检查（K8s liveness probe）
- `/actuator/health/readiness`：就绪检查（K8s readiness probe），包含 DB/Redis/MQ 连通性