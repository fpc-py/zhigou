# 故障演练与 Runbook

> 原则：**演练验证的是「恢复路径」，不是故障本身**。每次演练产出一份带实测数据的复盘记录，
> 沉淀到 `docs/测试问题bug记录.md`，作为面试「怎么证明系统可运维」的核心证据。
>
> 本文件配套脚本：`scripts/fault-drill/`（Python，探针式，无第三方依赖）。

---

## 一、演练总则

| 项 | 约定 |
|---|---|
| 窗口 | 生产演练走灰度窗口（`docs/gray-release.md`）；本地演练随时可跑 |
| 注入 | 优先用「停服务/断端口/清缓存/停消费者」等可逆操作，禁止直接删数据 |
| 观察 | 演练前记录基线（指标/日志），注入后观察信号（告警/降级/错误率），恢复后核对 |
| 复盘 | 每个场景 5 个问题：发生了什么 / 怎么发现 / 影响多大 / 怎么恢复 / 下次怎么更快 |
| 红线 | 演练中不改代码；发现缺陷记 bug（`docs/测试问题bug记录.md`），修复后回归再演练 |

---

## 二、Runbook 速查表

| 故障 | 关键信号 | Runbook（处理步骤） |
|---|---|---|
| **下游服务宕机** | BFF 5xx 率上升、Resilience4j circuit_open 指标、下游 health 失败 | ① 确认影响面（Grafana RED）② 查最近变更（CHANGELOG/git log）③ 熔断已开则等半开自愈（默认 5s），未开则按 `docs/monitoring-alerting.md` 定级 ④ 恢复后观察错误率回落 ⑤ 复盘 |
| **数据库不可用** | 连接池耗尽（hikaricp pending > 0）、P95 飙升、SQL 超时 | ① 确认 DB 层（MySQL 探针）② 服务应快速失败而非堆积（连接池超时配置）③ 恢复 DB 后看连接池回收 ④ 检查是否存在雪崩（限流兜底见工作流3） |
| **MQ 积压** | `rocketmq_consumer_accumulation > 1000`（P2 告警） | ① 查消费者是否存活/异常 ② 看死信/重试日志（消费异常会重试 16 次）③ 修消费者后观察 lag 回落 ④ 积压期间业务靠「主通道同步 + MQ 兜底」双通道不丢（见 outbox 设计） |
| **缓存击穿/雪崩** | 热点 key 回源 QPS 突增、DB 慢查询 | ① L1 Caffeine 单飞兜底（同一 key 仅一个线程回源）② L2 Redis 空值短缓存防穿透 ③ 确认缓存 TTL 抖动与失效一致性 ④ 复盘热点 key 清单 |
| **AI 服务超时/不可用** | BFF /chat SSE 断流、LLM 超时 | ① 超时兜底（llm_timeout）② fallback_handler 降级文案 ③ 输入注入/防编造护栏状态核对 ④ 恢复后复测 SSE 事件序列 |

---

## 三、演练场景与脚本

### 3.1 下游宕机 → 熔断降级（drill_circuit_breaker.py）

- 前置：BFF(3000) + 任一依赖服务运行；`%TEMP%\zhigou-jwt.txt` 有 token
- 注入：`Stop-Process` 停目标服务
- 验证信号：BFF 返回降级响应（非 5xx 洪泛）；恢复服务后自动回稳
- 退出：脚本自动重启服务并复测

### 3.2 缓存雪崩/击穿 → Caffeine 单飞（drill_cache_storm.py）

- 前置：product-service(8083) 运行；Redis 可用
- 注入：`FLUSHDB` 清缓存 + 并发 100 请求打同一 SPU 详情
- 验证信号：DB 回源次数 = 1（Caffeine 原子加载单飞）；恢复后命中率回归
- 注意：DB 回源次数从 product-service 日志 grep `loadDetail` 计数

### 3.3 MQ 积压 → 探针指标（drill_mq_backlog.py）

- 前置：RocketMQ broker + inventory-service(8086) 运行
- 注入：暂停消费者进程 → 向 ORDER_CLOSED 发 N 条消息
- 验证信号：`rocketmq_consumer_accumulation` 从 0 增长到 N；恢复消费者后回落 0
- 注意：本地无 Prometheus 时直接读 inventory 日志「MQ 积压采样: …lag=…」

---

## 四、on-call 值班流程（告警触发后）

1. **确认影响面**：Grafana RED 面板看错误率/延迟波及哪些服务；Loki 按 `traceId` 检索失败日志
2. **初步定位**：看最近一次发布/变更（`CHANGELOG.md`、git log）；业务指标异常优先查 outbox/MQ/对账
3. **定级上报**：P1 立即通知发布负责人；P2/P3 按窗口处理（定级表见 `docs/monitoring-alerting.md`）
4. **处置**：按 Runbook 速查表匹配；配置问题改配置重启；代码缺陷走修复+灰度
5. **复盘**：记录到 `docs/测试问题bug记录.md` 与 `docs/progress.md`，缺陷转 `docs/智购开发任务差距分析报告.md` 附录 B（未完成任务清单）

---

## 五、演练复盘模板（每次演练必填）

```markdown
### 演练 N：<场景>（<日期>）
- 前置环境：<服务/版本/数据量>
- 注入方式：<命令>
- 观测信号与实测数据：<指标：before → during → after>
- 发现的问题：<缺陷/盲区，无则写「未发现」>
- 恢复时长：<分钟>
- 改进项：<下次演练/产品改动>
```

---

*本文档路径: `docs/故障演练与Runbook.md`*
*相关文档: `docs/monitoring-alerting.md`（指标/告警）、`docs/gray-release.md`（灰度）、`docs/测试问题bug记录.md`（复盘留痕）*
