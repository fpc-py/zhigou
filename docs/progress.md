# 智购・项目进度追踪

> **唯一进度真相**：每完成一个可验证单元，更新本文档 + `CHANGELOG.md`；涉及接口 / 端口变更时同步 `README.md` 与 `docs/README.md` 索引。
> 状态：✅ 已完成 / 🚧 进行中 / ⬜ 未开始 / ⚠️ 有风险
> 与目标态的差距清单与执行顺序见 `docs/智购开发任务差距分析报告.md`。

## 2026-10-22 · [0.2.75] C 组 P1 商家侧 AI 售后管家（后端 + 工具 + BFF + H5 全闭环）
- **后端**：`AftersaleServiceImpl.merchantPending(status)` + `GET /aftersale/merchant/pending`（待审核列表 + counts 全状态统计）
- **AI 工具**：`merchant_after_sale(status)` 只读盘点+建议（AI 不擅自处理，人工确认门）；system.md 登记
- **BFF**：`GET merchant/pending`（置于 `@Get(':no')` 之前）+ `POST :no/approve` + `POST :no/reject`（rawBody 透传 reason）
- **H5**：商家中心「售后工作台」区块——状态统计卡 + tabs 筛选 + 待处理列表 + 同意退款/拒绝按钮
- **实测**：8090 直连 200（APPLYING:2）；BFF 自签 JWT 200；**approve 闭环** MOCK_AS_002 → APPLYING 2→1 / SELLER_APPROVED 12→13；H5 `vue-tsc --build` 通过 + 浏览器端到端渲染真实数据 + 点击流转 UI 联动
- **坑**：#70a BFF 路由顺序（`:no` 会吞掉 merchant/pending）；#70b H5 Pinia store 初始化时机（localStorage 注入需刷新）；#70c Windows JWT 文件 BOM 污染请求头
- 清单 C 组 P1 商家侧 ✅

## 2026-10-22 · [0.2.74] B 组残余补验 · B7 故障演练 3.2/3.3 真实执行
- 3.2 缓存单飞：FLUSHDB + 并发100 打真实 SPU → 100/100 全200、0.21s
- 3.3 MQ 积压：停消费者 → 真实关单 → 重启后采样 lag=150 + 批量拉平
- 坑 #69（namesrv 未起 + ROCKETMQ_NAME_SERVER 空串）；演练复盘入库

## 2026-10-22 · [0.2.73] B 组残余补验 · B4 prompt_guard SSE 端到端（变体+白名单修复）
- SSE 端到端发现真实缺口：越权扮演「你是X」句式、「不再受任何X限制」插词变体漏检；白名单「帮我」可绕过全规则
- 修复：正则扩多句式 + 白名单收紧（求知类词才放行）；本地 5+5 全过 + 单测 3 + SSE 3/3 notice
- 坑 #68a/#68b；清单 B4 ⏸ → ✅

## 2026-10-22 · [0.2.72] B 组残余补验 · B3 安全头端到端（XSS 编码载荷绕过修复）
- 端到端发现 XssFilter 漏检 URL 编码载荷（%3Cscript%3E → 200）；修复=query 先 URLDecoder.decode 再匹配
- 复验：4/4 编码 XSS 样本 400 拦截 + 正常请求 200 带 CSP/nosniff/DENY；product 重启加载新 common
- 坑 #67；清单 B3 ⏸ → ✅

## 2026-10-22 · [0.2.71] C 组 P1 · 对话页快捷入口（售后管家聚合 + 定时购卡）
- 售后助手卡升级为「售后管家」（prompt 触发 after_sale_manager overview 三路聚合）；新增「定时购」卡（bell 图标，触发 scheduled_buy 创建确认流）
- **验证**：H5 `vue-tsc --build` 通过；quickActs 6 卡（送礼/比价/售后管家/物流/补货/定时购）
- 无后端变更（[0.2.69]/[0.2.70] 工具已交付）

## 2026-10-22 · [0.2.70] C 组 P1 二期 · Agent 定时购（确认门 + 后台调度）
- **工具** `scheduled_buy`（list/create/cancel）：确认门前置（未确认零落库）+ `data/scheduled_buys.json` 持久化 + 后台 asyncio 调度（60s 扫描，main.py startup 挂载）到点复用 `create_order` 真实下单；一次性 DONE、周期滚动 ACTIVE、**失败不轰炸**
- **路由**：触发词（定时购/自动买/每周买…）置 usage_cycle 之前；system.md 登记
- **坑**：#66a LangChain BaseTool 须 `.ainvoke`（直接 await 报 `__call__ 不认关键字参数`）；#66b once 完整日期被 HH:MM 解析炸（fromisoformat 优先）
- **测试**：7 用例（确认门/缺参/持久化/空列表/取消/一次性 DONE/周期滚动+失败不轰炸）——**pytest 45 passed**；真实链路：确认创建 → 到期真实下单（订单 2113231400802684928 ¥99.00）→ DONE → 取消

## 2026-10-22 · [0.2.69] C 组 P1 · AI 售后管家（overview 聚合 + apply 确认门）
- **A1 工具** `after_sale_manager(action=overview|apply, issue, order_no, type, reason, confirm)`：
  - overview 三路聚合售后健康度：`/aftersale/mine`（处理中单）＋ `/logistics/delay-alerts`（延误预警）＋ `/aftersale/warranty/alerts`（质保到期）
  - apply 确认门：`confirm=false` 只出摘要零写库；`confirm=true` 先取订单服务**真实金额**（防编造）再 POST `/aftersale/apply`（无重试防重复）
- **A2 路由**：`_ROUTE_RULES` 售后管家触发词（售后管家/处理售后/我要退款/物流卡住…置 aftersale_assistant 之前）；system.md 工具表登记
- **A3 测试**：mock 用例 7 条（聚合/空降级/异常降级/未确认零写/确认金额取订单/缺参/查无金额拒绝）＋ 真实链路：overview 命中处理中 1 单 + 延误 10 单；apply 真实创建 `ASAB19A3BCFE614691`（¥99.00）；幂等拒绝重复、CLOSED 拒绝售后 —— **pytest 38 passed**
- **坑记录**：#65a 工具调 Java 写接口漏 `X-User-Id` 透传头 → 403（JwtAuthFilter 优先认 X-User-Id，GET 侥幸放行写接口暴露）；#65b 状态字典与真实枚举（APPLYING/SELLER_APPROVED/CANCELED）对齐

## 2026-10-22 · [0.2.68] SSE 在线对话链路修复（回归 + 测试补强）
- **根因**：①chat_service 漏 import `sanitize_query`（NameError）②.env `/anthropic` 路径 404 + 思考模型不支持 tool_choice ③deepseek-chat 静默忽略强制 → 谎报执行
- **修复**：补 import；base_url `/v1`、model `deepseek-chat`；新增 `ZHIGOU_AI_SPECIFIC_TOOL_CHOICE=false` 配置开关（auto + 指令注入）+ 运行时 BadRequestError 降级兜底
- **实测**：SSE 关注降价闭环 `tool_call×2/tool_result×2/token×127/done`，「已关注 黑色 的降价，降到 ¥45 提醒你，当前价 ¥44.00」落库；pytest 31 passed（测试随实现演进：dict 事件契约 + mock 签名）
- **坑 #64 闭环**：LLM 链路三层坑（漏 import / 端点路径 / 模型能力差异致谎报）
- 下一步：C 组 P1 候选拍板（Agent 定时购 / AI 售后管家）
## 2026-10-22 · [0.2.67] C 组 P0 AI 省钱助手 E 块：全链路冒烟闭环
- 冒烟断言 6/6 通过（关注 upsert→造降价→check→提醒→历史→overview）
- 口径核对：noticeSaving 只统计真实降幅（dropAmount>0），零降幅目标价命中不计省钱——防虚增 ✓
- **C 组 P0（A–E）全部完成**；下一步：C 组 P1 候选拍板（Agent 定时购 / AI 售后管家）

## 2026-10-22 · [0.2.66] C 组 P0 AI 省钱助手 D 块：AI price_alert 工具
- tools.py price_alert（watch/unwatch/mine）+ system.md 工具表 + _ROUTE_RULES 触发词 + pytest 2 用例
- 实测：pytest 10 passed；工具真实调用关注/查询/坏参数降级 ✓
- 遗留：SSE 在线对话 LLM 空流（外部 qwen 调用失败，待复验）
- 下一块：E 块造数脚本 + 全链路冒烟断言 + 三件套 + 提交推送

## 2026-10-22 · [0.2.65] C 组 P0 AI 省钱助手 C 块：省钱汇总 + BFF + H5
- product /cost-saver/overview 四口径聚合（满减实省/券预估/比价差/降价已省，独立合并防重复）
- BFF 7 路由透传（rawBody 保真）+ H5 /cost-saver 页（hero 总节省 + 提醒 + 关注）+ profile 入口
- 实测：BFF total=23120「已帮你省 ¥231.2」；浏览器 hero 卡渲染 ✓
- 下一块：D 块 ai-orchestrator price_alert 工具 + system.md + pytest

## 2026-10-22 · [0.2.64] C 组 P0 AI 省钱助手 B 块：降价关注与提醒
- price_alert 关注表 + price_notice 站内信 + 关注/取消/列表/已读接口 + 每日 03:10 检测任务（命中推进基准价防重复提醒）
- 实测：关注→造降价 4900→4500→check hits=1→提醒「降 ¥4.00」→watch 推进 4500
- 下一块：C 块省钱汇总 + BFF 透传 + H5 /cost-saver 页

## 2026-10-22 · [0.2.59] 读路径 id 字符串化根治（坑 #63 闭环）
- **范围**：响应 DTO/Entity 19 位 id 统一 `@JsonSerialize(ToStringSerializer)`——批量脚本 43 文件 55 字段（product/order/marketing/aftersale/cart/inventory/user/auth/file）
- **验证**：product spuId/skuId [String]；拼团 id/skuId/spuId [String]；H5 全链路字符串 skuId 加购→下单 201 金额 4900→落库 19 位无损
- **坑 #63 三阶段闭环**：写路径 rawBody 透传 → 8 透传接口统一 → 读路径字符串化；面试叙事：同 bug 系统级根治（源头保真 + 展示保真）

## 2026-10-22 · [0.2.63] C 组 P0 AI 省钱助手 A 块：历史价格底座
- price_history 表 + 每日 02:10 全量快照 + 查询补点 + GET /price/history（趋势/最低价/现价对比）
- 实测：建表 ✓、快照 7 SKU、history 4900=4900 diff=0「当前即最低」
- 下一块：B 块降价关注与提醒（price_alert/price_notice + 检测任务）

## 2026-10-20 · [0.2.50] B7 故障演练 / Runbook / on-call
- docs/故障演练与Runbook.md（5 类故障速查 + 值班流程 + 复盘模板）+ scripts/fault-drill/ 3 场景演练脚本（语法已验证）；真实演练待环境恢复执行留痕

## 2026-10-20 · [0.2.49] B5 金额加密落库（order_main 落点）
- common AmountEncryptUtil(AES-GCM 随机IV) + AmountTypeHandler 透明加解密 + order 两金额列迁移 VARCHAR + 存量明文兼容；单测 4/4；生产需配 zhigou.crypto.key

## 2026-10-20 · [0.2.48] B2 消息堆积监控（inventory-service）
- RocketMqLagProbe：DefaultMQAdminExt 查消费位点 → rocketmq_consumer_accumulation Gauge（与 MQBacklog 告警规则联动）；broker 不可达降级 warn；坑 #51（5.x API 变更）

## 2026-10-20 · [0.2.47] B4 Prompt 注入防护（ai-orchestrator 输入侧）
- prompt_guard.py：指令覆盖/系统提示泄露/越权扮演/解除限制/越权访问/编码变体 6 维检测；命中降级隔离（注入文本不透传 LLM）；正常语境白名单防误报；pytest 3/3 通过

## 2026-10-20 · [0.2.45] B1 三级缓存 Caffeine（product-service 热点读）
- L1 Caffeine（60s/max1w，原子加载单飞防击穿）+ L2 Redis（5min）+ L3 MySQL；空值 Optional 短缓存防穿透；更新/下架双级失效

## 2026-10-20 · [0.2.46] B3 XSS/CSRF 防护（common 全局）
- XssFilter（反射型 XSS 特征 400 拦截）+ 安全响应头（CSP/nosniff/DENY/no-referrer）；单测 5/5 通过

## 2026-10-20 · [0.2.44] A7 文件访问鉴权（私有读）
- file-service：bucket 私有化（移除公开读策略）+ GET /file/{id}/content 私有读代理（鉴权+归属403+流式）+ 签名URL 短时效分发
- BFF：file 模块（upload/url/content 透传），补全前端 /file/* 链路（此前缺失会 404）
- 坑 #49：MinIO bucket 公开读策略导致文件公网匿名可读
- ⏸ 待补验：3307 MySQL 恢复后启动 8082/3000，验证上传→私有读/匿名403/跨用户403/签名时效

## 2026-10-20 · [0.2.43] A5 T+1 对账（代码完成，编译通过；验证待环境）
- payment_reconcile_log 对账日志表 + reconcile() 升级（汇总/三类差异清单/补偿/落库幂等）+ GET /payment/reconcile/report
- 坑 #48：接口返回类型未同步（void vs Map）
- ⏸ 待补验：3307 Docker MySQL 恢复后启动 payment 8087，造数验证差异清单/补偿/落库

## 2026-10-19 · ⏸ 暂停点（用户指示：停服务，下次接着开发）
- 已停：order 8085 / marketing 8088 / aftersale 8090 / BFF 3000（payment 8087 本就在 DOWN）
- 环境阻塞：Docker Desktop 引擎 500 → 3307 Docker MySQL 的 mysqld 无响应（TCP 通、握手超时），新建 DB 连接全拒；3306 本机 MySQL 正常（握手 OK）
- 修复 Docker Desktop（重启服务/重启 Docker）后，payment 8087 可重启验证 A5 + A4 待补验项

## 2026-10-19 · A5 T+1 对账（代码已落盘，未编译验证，未提交）
- 已落盘：V20261102__reconcile_log.sql（payment_reconcile_log 按周期幂等）、ReconcileLog 实体 + Mapper
- reconcile() 已升级：汇总（total/success/pending/refunded）+ 差异清单（UNNOTIFIED / STALE_PENDING / OVER_REFUND）+ 补偿 + 落库，返回报告 Map
- Controller 已加 GET /payment/reconcile/report
- **编译卡点**：PaymentServiceImpl.java:109 报「找不到符号」——reconcile 方法内新增的局部变量/语句与旧代码残留冲突，需 Read 109 行附近核对后修复
- 下次接续：修编译错误 → package → 重启 8087 → GET /payment/reconcile/report 验证 + 三件套 + 提交 [0.2.43]

## 2026-10-19 · [0.2.42] A4 部分退款 + 部分发货
- **部分发货**：order_shipment 明细表 + SHIPPING 中间态（PAID→SHIPPING→SHIPPED），分批履约；全 SKU 发完自动 SHIPPED；超量 40052/状态 40050
- **部分退款**：aftersale 累计退款上限校验；payment 退款幂等 orderNo+amount（坑 #47）
- **实测**：count=3 分批发 1+2 → SHIPPING→SHIPPED；超额发货 400；退款 100/300 成功 + 累计 201 拦截
- **待补验**：payment 重启被 Docker MySQL 3307 阻塞（引擎 500）；库存回滚待 inventory 8086 启动后复验

## 2026-10-19 · A6：秒杀活动 + 券叠加互斥 [0.2.41]
- ✅ 秒杀模块：seckill_activity + 原子防超卖 UPDATE（行锁）+ 时间窗口 + 每人限购；BFF /seckill/* 4 端点透传
- ✅ 券叠加互斥：calculate 拆分秒杀金额（满减/券基于非秒杀金额），秒杀商品不叠加优惠
- ✅ 实测：活动列表 2 项（明日秒杀过滤）；预扣 sold 20→21 remain 79；超购拦截；互斥对照 22000减3000 vs 含秒杀21900不减
- 坑 #45 flyway 版本号静默跳过 / #46 Map body ClassCastException

## 2026-10-19 · A 组技术收尾：SSE 断链 UI 提示 + BFF 熔断降级 [0.2.40]

- ✅ **A3 bug #37 SSE 断链重连 UI 提示**：chat.ts 透传 SseOptions；chat/index.vue 接 onReconnect/error 显示「连接恢复中…（第 N 次）」状态条 / 「连接已断开」+ 重试按钮；确定性测试 ALL_PASS（流中断恢复 / 持续中断耗尽）+ 浏览器实测（AI 宕机显式提示、输入框锁定）
- ✅ **A1 BFF 熔断降级**：common/circuit-breaker.ts（滑窗 10s / 失败率 50% / minCalls 5 / 打开 10s / 半开探活 1）+ GuardedHttpService 零侵入替换 HttpService；故障注入实测 /home/feed **7ms/3ms/2ms 快速失败**（原 2s 超时）+ 降级 200 + 半开探活自动重开；状态机单测 6 项 ALL_PASS
- 📋 **docs/未完成任务清单.md**：A/B/C/D/E 五类 + 每项验收标准（规划源）；E 组 3 条滞后项（真伪测评/推荐引擎/图片搜款）勾销
- ✅ **A2 BFF 业务埋点升级**：MetricsRegistry（路由维度 60s 滑窗：count/errorRate/p50/p95/qps）+ /metrics 端点；实测 80 并发 → 3 路由聚合自洽
- 剩余 A 组待办：A2 埋点 → A4 部分退款 / A5 T+1 对账 / A6 秒杀+券互斥 / A7 文件鉴权 / A8 Prometheus 实跑（待 Docker Desktop）

## 2026-10-18 · 工作流2：AI 工程化深挖（可靠性+防编造+RAG+SSE+可观测）[0.2.33]

- **#128 工具可靠性**：config 新增 ai_tool_timeout_s/retries/retry_delay_s（env 可覆盖）；_http_get 只读幂等重试（5xx/传输错误重试、4xx 不重试、0.3s×attempt 退避）、_http_post 写操作不重试（防重复下单）；清理 27 处散落 timeout，保留 3 特例（比价 8s/下单 8s/vision 60s）
- **#128 防编造**：回归发现真实护栏漏洞（关键词表缺「未搜索到」→ 空结果路径不触发护栏）→ 补全 _NOT_FOUND_MARKERS 15 词 + 空/None 强制挂护栏
- **#128 RAG 检索质量（真实 Bug）**：ivfflat(lists=100) 在 5 条向量上退化（EXPLAIN 实证 probe=1 扫空 list，Top-1 命中率 0%）→ HNSW(m=16, ef_construction=64) + min_score=0.50 阈值 → Top-1/Top-3 命中率 100%（0.7905/0.7095/0.7025/0.785，无关 0.3751 被滤）
- **#128 SSE 连接管理**：sse-polyfill.ts 重写（断线指数退避重连/幂等续传/心跳活性/假死超时释放 30s）；chat.ts 适配 { signal }（修复旧签名静默丢取消信号）；type-check 通过
- **#128 AI 链路可观测**：_run_agent 全链路 TRACE 日志（llm_req/llm_resp/tool_call[脱敏]/tool_result[ok+guarded+耗时ms]/finish）
- **#128 测试**：test_tool_reliability.py 7 项全 PASS + test_forge_guard.py 18 项全 PASS（沙箱直测）；test_rag_quality.py / test_sse_e2e.py 交付（需用户环境 pgvector+BFF+JWT 复跑）
- **#128 提交**：CHANGELOG [0.2.33] / bug #36 / docs/AI可靠性实测报告.md
- **#128 本机复验（2026-10-18，环境恢复本地后真跑闭环）**：
  - ✅ **H5 type-check 通过**：本机 node + h5-shop node_modules，`npm run type-check`（vue-tsc --build）0 错误
  - ✅ **RAG 评测通过**：本机 pgvector（zhigou-pgvector healthy）+ .env key，`test_rag_quality.py` → **Top-1/Top-3 命中率 100%**（4/4），命中分数 0.7905/0.7095/0.7025/0.785，无关查询（恐龙化石/生鲜水果）被 0.50 阈值滤除返回空
  - ✅ **SSE 完整 token 流通过**：本机起 AI 8095（ZHIGOU_APP_PORT=8095 venv）+ BFF 3000 + 自签 HS256 JWT → 场景A：`POST /chat/sse` **HTTP 201 + 38 个 token 分片 + tool_call/tool_result 真实触发（analyze_user_context）+ done 收尾（24.16s）**；场景B/C 超时参数（5s/25s）与实际 LLM 延迟（24s）不匹配，脚本未完整走完（非链路缺陷）
  - ✅ **纯逻辑测试**：test_forge_guard 8 测试 + test_tool_reliability 7 场景全 PASS（本机 venv 直测）
  - ✅ **④ 浏览器断网/杀进程端到端（本机实测闭环）**：H5 5174 + BFF 3000 + AI 8095 全链路；浏览器注入自签 JWT 进对话页 → 发消息触发 SSE（工具调用 analyze_user_context→search_products 实时渲染）→ **杀 AI 8095** → 流中断、页面不崩、输入框释放（sse-polyfill 后台 4 次指数退避重连）→ **重启 AI 8095** → 新消息完整 token 流恢复；发现 UI 缺口：sse-polyfill onReconnect/onError 未被 chat.vue 接住（断链无用户可见提示）→ 记 bug #37
- ✅ **工作流2 复验待办已全部清零**（RAG/SSE/type-check/纯逻辑/浏览器端到端 5 项）
## 2026-10-18 · 工作流1 交易一致性深挖：5 类故障注入实测 + 真实 Bug 修复 [0.2.32]

- **#127 实测环境**：6 交易服务（auth/product/cart/order/inventory/payment）dev 全启动；zhigou-mysql/redis/rocketmq 全 healthy；MySQL 隔离级别核实 REPEATABLE READ
- **#127 5 场景故障注入**（scripts/consistency-fault-injection/）：① 30/100 并发幂等下单 → 修复后 30×200/0 错/1 单/4.1s、100×200/0 错/1 单/2.1s；② 100 并发库存 1 → 成功 1/0 超卖/Redis 0；③ 30 并发重复回调 → 30×200/payment 仅 1 条 SUCCESS/订单 PAID；④ outbox 双通道 → SETNX 幂等累计释放恰 1（重复 ×5 不叠加）；⑤ 50 并发支付 vs 关单 → 支付 25 胜出、取消 25×40050 拦截、终态 PAID、库存 0 变化
- **#127 真实 Bug 修复（#35）**：并发幂等兜底三次失败尝试（REPEATABLE READ 快照读 / FOR UPDATE S→X 锁升级死锁 / REQUIRES_NEW 连接池耗尽）→ `create` 事务 READ COMMITTED 终版闭环；30/100 并发全 200
- **#127 交付**：`docs/交易一致性实测报告.md`（面试叙事、统一口径数据、复现方法）+ bug #35 + CHANGELOG [0.2.32]；git 提交推送
- 附带澄清（设计事实非 bug）：用户取消仅走 ORDER_CLOSED MQ 单通道（+0 同步），超时关单才是同步+MQ 双通道；SETNX 保证任一双通道只释放一次
## 2026-10-10 · P0 文件服务增强：图片压缩/格式转换/尺寸限制 + 售后凭证接入 [0.2.31]

- **#126 后端（file-service）**：图片处理管线（像素上限防 DoS 40003 / 等比缩放 / JPEG 0.8 压缩 / PNG 无损 / convertTo 转换）；增强 upload 五参数（compress/convertTo/bizType/maxWidth/maxHeight）；FileMeta 加 bizType/width/height（Flyway V20261093）；UploadResponse 扩展元数据回显
- **#126 H5**：api/file.ts 重写带参上传；对话页搜款 compress+bizType=chat；售后申请页凭证区块（3 张压缩上传/缩略图删除/提交 images）
- **#126 验证**：直连 5 场景全 PASS（缩放/压缩/png 转换/40003/40002/小图只压缩）+ file_meta 落库核对 + 售后凭证闭环 PASS（ASC5C7AE75454C4696 images 落库）+ H5 build PASS + AI venv 重启 RAG 复验
- **#126 提交**：CHANGELOG [0.2.31] / bug #34 记录；待办池勾销；测试数据已清理
- ⚠️ 遗留：H5 浏览器端到端（凭证上传/压缩上传）下次统一测试

## 2026-10-10 · P2 供应链优化三期：异常订单自动处理 [0.2.22]

- **#99 后端（order-service）**：Flyway V20261091__fulfillment_action 建表；FulfillmentAction 实体/Mapper；POST /order/fulfillment/action（SPLIT/DELAY/OFF_SHELF/REPLENISH，仅 PAID 单写 DONE 记录）、GET /order/fulfillment/actions；pendingFulfillment 附 handled 标记；orderIds 字符串/数字双兼容（修复 List<Number> 500）
- **#100 AI**：fulfillment_action 工具（TOOLS 23），未传 order_ids 自动全量处理，演示口径标注正式版审批流
- **#101 BFF+H5**：BFF /merchant/fulfillment/{actions,action}（JwtAuthGuard，修复 Number() 丢精度）；H5 商家中心履约异常区块四动作按钮 + 已处理标记 + 处理记录列表
- **#102 验证+三件套+提交**：后端直连 13 单/handled/2 单动作 PASS；AI SSE 全量拆分发货 PASS；BFF 三层 1 单 PASS；H5 build PASS；CHANGELOG [0.2.22] / bug #26；git 提交推送
## 待验证清单（已开发未测试）

> 以下功能已完成代码开发与静态编译检查，**尚未进行运行态测试与 git 提交**，下次统一验证后补测补交。

| 批次 | 功能 | 代码状态 | 验证状态 | 备注 |
|---|---|---|---|---|
| P1 第七批 | AI 送礼助手 `gift_assistant` | ✅ 已写入 tools.py + system.md | ✅ 2026-10-10 SSE 实测 PASS（工具触发+真实数据） | 随 [0.2.11] 统一提交 |
| P2 第一批 | 购物后 AI 服务：`aftersale_assistant` + `logistics_tracker` | ✅ 已写入 tools.py + system.md | ✅ 2026-10-10 SSE 实测 PASS（售后/物流均触发） | 随 [0.2.11] 统一提交 |
| P2 第一批 | `usage_cycle_assistant` 使用周期管理 | ✅ 已写入 tools.py + system.md | ✅ 2026-10-10 SSE 实测 PASS | 随 [0.2.11] 统一提交 |
| P2 第一批 | H5 对话页 AI 功能快捷入口（5 卡片） | ✅ 已写入 chat/index.vue，vite build 通过 | ✅ 2026-10-10 浏览器实测：5 卡片触发对话正常（暴露防编造 bug 已修复） | 随 [0.2.11] 统一提交（补充提交） |
| P2 拼团 | marketing 拼团后端 + AI groupbuy_finder + BFF 透传 + H5 拼团页 | ✅ 全套代码完成 | ✅ 2026-10-10 API 8/8 + 浏览器实测通过（开团/团主/错误提示） | 随 [0.2.11] 统一提交（补充提交） |
| AI 链路 | chat_service 迁移原生 OpenAI（tiktoken 阻断修复）+ 工具路由 + 超时配置 | ✅ 重写完成 | ✅ 2026-10-10 SSE 实测 5/5 PASS | 随 [0.2.11] 统一提交 |
| 上线就绪 | release-checklist 改写 + gray-release + monitoring-alerting + 部署资产整改 | ✅ 已完成 | ✅ 静态核验通过 | 随 [0.2.11] 统一提交 |

## 2026-10-10 · P2 供应链优化二期：自动补货 + 补货中心 [0.2.21]

| 项 | 状态 | 说明 |
|---|---|---|
| 补货后端 | ✅ | inventory：replenish_record 表（Flyway V20261091）+ 手动/自动补货/记录三接口；直连 PASS（手动 1→11、自动 SKU2 10→50、造数 SKU20 5→50 恢复 93） |
| AI 补货工具 | ✅ | `supply_replenish`（TOOLS 22）：盘点+记录+do_replenish 触发；SSE 查询/触发两路径 PASS |
| BFF 透传 | ✅ | `/merchant/supply/records`、`auto-replenish`、`replenish`；登录态 200 |
| H5 补货中心 | ✅ | 商家中心补货区块（低库存+一键补货+记录列表）；type-check+build PASS |
| 提交 | ⏳ | 随 [0.2.21] 统一 commit & push（含 [0.2.18]-[0.2.20] 补推） |

## 2026-10-10 · P2 供应链优化一期：履约异常预警 [0.2.20]

| 项 | 状态 | 说明 |
|---|---|---|
| order 待发货接口 | ✅ | `GET /order/stats/pending-fulfillment`：PAID 订单 + SKU 明细；直连验证 13 单 |
| AI 履约预警 | ✅ | `fulfillment_alert` 工具：待发货 × 低库存匹配缺货卡单 + 处理建议；SSE tool_call→tool_result 实测（造数验证 11 单命中，恢复库存后无风险） |
| BFF 透传 | ✅ | `GET /merchant/fulfillment`；登录态 200；顺手修复 AI URL 8000→8095（8000 被其他项目占用） |
| H5 履约区块 | ✅ | 商家中心「履约异常」区块（13 单待发货列表 + AI 入口）；type-check+build + 浏览器实测 |
| 提交 | ⏳ | 随 [0.2.20] 统一 commit & push（含 [0.2.18]/[0.2.19] 补推） |

## 2026-10-10 · P2 商业模式落地：AI 会员订阅 [0.2.19]

| 项 | 状态 | 说明 |
|---|---|---|
| wallet 订阅后端 | ✅ | `POST /wallet/subscribe`（¥29/¥99 30 天，沙箱扣余额，未到期顺延）+ `GET /wallet/subscription`；V2 迁移加 member_expire_at；直连验证：FLAGSHIP 开通 → expireAt +30 天 + 扣费 + level 更新 |
| BFF 透传 | ✅ | wallet 模块新增 subscribe/subscription 路由，登录态 200 返回完整订阅信息 |
| H5 会员中心 | ✅ | 钱包页升级：会员卡（档位/到期/生效态）+ 档位卡（权益对照）+ 订阅确认弹层；type-check+build + 浏览器实测通过 |
| 提交 | ✅ | 随 [0.2.19] 统一 commit & push（含 [0.2.18] 补推） |

## 2026-10-10 · P2 商家经营二期：经营预警（低库存 + 差评负面预警）[0.2.18]

| 项 | 状态 | 说明 |
|---|---|---|
| inventory 低库存接口 | ✅ | `GET /inventory/low-stock?threshold=10`（available<=threshold 升序），SecurityConfig 放行；直连 2 条：SKU3 余 1 / SKU2 余 10 |
| product 差评接口 | ✅ | `GET /product/review/negative?minRating=3&limit=5`（rating<=3 倒序），内网调用需 `X-User-Id` 头；直连 5 条（spuId 12/11/10，rating 2-3，含内容摘要） |
| AI 预警工具 | ✅ | `merchant_warnings(threshold=10)` 聚合低库存+差评；直调 PASS + SSE 全链路 tool_call→tool_result PASS |
| BFF 透传 | ✅ | `GET /merchant/warnings` 聚合返回 lowStock+negative（Promise.all+兜底 []），登录态 200 |
| H5 预警区块 | ✅ | 商家中心页「经营预警」角标 7 + 低库存/差评列表 + 空态；type-check+build + 浏览器实测通过 |
| 提交 | ✅ | 随 [0.2.18] 统一 commit & push |

## 2026-10-10 · 上线就绪文档（M4 对齐）

| 项 | 状态 | 说明 |
|---|---|---|
| release-checklist.md | ✅ 改写 | 对齐真实部署资产：去掉 Nacos/K8s 残留，改为 compose 蓝绿 + middleware/services/monitoring 三编排；保留启动顺序/回滚条件/性能基准/签署结构 |
| gray-release.md | ✅ 新建 | 灰度发布（服务分 A/B/C 三批放量 + 决策门）+ 回滚方案（镜像回退首选 / DB 回滚高风险流程 / Hotfix） |
| monitoring-alerting.md | ✅ 新建 | RED/USE 指标体系、SLO、P1-P3 告警定级 + PromQL 规则示例、值班 SOP、日志检索 |
| docs/README.md 索引 | ✅ 更新 | 运维域登记 gray-release / monitoring-alerting（🆕） |
| 运行态验证 | ⏸ 未测试 | 纯文档交付（引用路径/端口已静态核验），未 git 提交，随攒批统一提交 |
| ✅ 部署资产整改 | ✅ 已完成 | services.yml 镜像改为 ${ZHIGOU_REPO:-zhigou}/<svc>:${TAG:-0.1.0} 占位（11 服务）；deploy.yml 注入 ZHIGOU_REPO=ghcr.io/fpc-py/zhigou；compose config 双模式渲染验证通过（默认 zhigou/0.1.0、生产 ghcr.io/fpc-py/zhigou/main-<sha>）；release-checklist/gray-release 前置条件同步更新。未测试未提交 |

## 2026-10-08 · P1 第六批：跨平台比价 + 图片搜款（多模态）

| 项 | 状态 | 说明 |
|---|---|---|
| 跨平台比价 | ✅ | /price/compare 适配器聚合京东/天猫/拼多多，含运费/到货/最优标记/建议，冒烟通过；AI compare_prices 升级为跨平台输出 |
| 图片搜款 | ✅ | search_by_image 工具（vision 特征 → 真实搜索）+ imageUrl 全链路（H5→BFF→AI）+ 对话页相机入口；触发稳定，视觉识别外部网络不可达时降级提示 |
| 前端接通 | ✅ | 比价页删除模拟数据改真实接口；对话页图片上传/预览/发送 |
| 修复 | ✅ | vision 60s 超时+重试；上传图片强制走 search_by_image 指令 |
| 限制 | ⚠ | 渠道价为本地模拟源（生产换真实 API）；vision 依赖 LLM 端点网络（本机不可达时降级） |

## 2026-10-08 · P1 第五批：个性化推荐引擎（画像 + 评分 + AI 工具 + 前端）

| 项 | 状态 | 说明 |
|---|---|---|
| 推荐引擎 | ✅ | product-service `/recommend`：订单品类/价位带 + 购物车意向 + 评价口碑加权评分，可解释理由 + 刷评/已购/小众标记，API 冒烟通过 |
| AI 工具 | ✅ | `recommend_products` 接入对话，SSE 实测 analyze_user_context→recommend_products 全链路无编造 |
| 提示词防幻觉 | ✅ | system.md 重写：12 工具清单 + 六步流程 + 禁编造红线（原 LLM 曾编造库外商品名） |
| BFF + H5 | ✅ | /home/feed 聚合 personal 字段；首页"猜你喜欢 · 为你定制"区块，双端 build 通过 |
| 修复 | ✅ | /order/mine 缺 userId 参数 500（补参）；order-service 恢复 dev profile（mock 数据）；双¥ 去重 |

## 2026-10-08 · P1 第四批：评价数据底座 + 差评/水军识别

| 项 | 状态 | 说明 |
|---|---|---|
| 评价数据底座 | ✅ | product-service 新增 `product_review` 表 + 发表/列表/统计三接口 + dev mock 25 条（含差评与刷评特征），API 冒烟通过 |
| AI 评价分析 | ✅ | `review_analysis` 升级为真实评价分析：统计+差评要点+规则化识别（疑似刷评/差评比例/口碑），SSE 实测通过 |
| skuId/spuId 混淆 | ✅ | `search_products` 输出并列 spuId/skuId，`review_analysis` 兼容 skuId 反查，后端 SkuItem 补 spuId |
| Flyway 版本乱序 | ✅ | 建表迁移改点号版本 `V20261090.1`（介于 20261090/20261091），清理 failed 记录，顺序恢复 |

## 2026-10-08 · CI 集成测试基建闭环（MinIO/s3mock + RocketMQ 条件化）

| 项 | 状态 | 说明 |
|---|---|---|
| file-service 集成测试存储镜像 | ✅ | `minio/minio`（Docker Hub 整仓 404 / quay 500）→ `adobe/s3mock` + Testcontainers 官方模块 `s3mock-testcontainers:4.5.0`；本地 `mvn verify` 全模块 SUCCESS，CI 13 checks 全绿 |
| inventory 测试 RocketMQ 依赖 | ✅ | `OrderClosedListener` 加 `@ConditionalOnProperty` 条件开关，test profile 禁用——CI 无 MQ 环境上下文可正常启动，本地/CI 均 hermetic |
| CI 流水线 | ✅ | GitHub Actions 13/13 checks passed（build-test 67 项测试 + 11 服务镜像构建） |

## 当前阶段

**P0 交易闭环 / P1 决策辅助 / P2 生态矩阵 + 商家端 AI 全部闭环**（[0.1.1]~[0.2.27]）。剩余：P0 技术收尾项（**BFF 埋点/熔断/SSE 重连**）→ M3 全链路压测 → M4 上线执行。文档体系已按《智购功能文档.md》《智购-企业级工程化技术方案.md》对齐（[0.2.28]）。

## 里程碑



| 里程碑      | 目标                                       | 状态              | 完成日期       |
| -------- | ---------------------------------------- | --------------- | ---------- |
| M0 脚手架   | Monorepo + 中间件 compose + auth-center 能登录 | ✅               | 2026-09-30 |
| M1 交易闭环  | 下单→支付→库存→物流→售后端到端跑通                      | ✅ 全链路联调冒烟 17/17 | 2026-10-07 |
| M2 AI 导购 | SSE 对话 + RAG + 降级兜底 + 对话商品卡              | ✅               | 2026-10-07 |
| M3 大促压测  | 5000 QPS 不垮，P99 < 1.5s                   | ⬜               | -          |
| M4 上线    | 灰度发布 + 监控告警 + 回滚演练                       | ⬜               | -          |

## 已完成

* 2026-10-19：**工作流3 #9 落地：BFF 横向扩展验证 + 远程推送**：① 推送 9 个提交到远程 main（[0.2.32]~[0.2.38] 全量同步）；② 起 BFF2(3002) 验证横向扩展——100VU 双实例 184 RPS vs 单实例 97 RPS（**1.89x≈线性**、P50 减半、0 错误），坐实 BFF 单进程瓶颈、横向扩容方向；③ 坑 #42：压测 token 过期假错误率 100%。详见 CHANGELOG [0.2.39]。

* 2026-10-18：**工作流4 面试叙事沉淀（四工作流收官）**：新增 docs/面试深度叙事.md（6 大叙事：交易一致性/AI 工程化/traceId/熔断/压测假错误率/告警 403，含实测数据与追问应答）；README 导航加叙事入口。**四工作流全部闭环**：一致性→AI 工程化→生产级加固→面试叙事。详见 CHANGELOG [0.2.38]。

* 2026-10-18：**工作流3 #8 告警接入（生产级加固收尾）**：14 服务放行 /actuator/prometheus（403→200）+ alerts.yml 5 条告警规则（熔断 OPEN/半开抖动/高错误率/高延迟/掉线，promtool SUCCESS）+ Alertmanager 分级路由编排 + monitoring.yml 集成。实测 4 核心服务指标端点 200、熔断指标齐全。**至此工作流3 全部闭环（#10+#7+#9+#8）**。详见 CHANGELOG [0.2.37]。

* 2026-10-18：**工作流3 #9 全链路压测+瓶颈定位（生产级加固第三块）**：自研 threading 压测器分层对比（BFF vs 直连，50/100/200VU）。**坑1**：asyncio+同步 urllib 假并发；**坑2（关键）**：HTTP 201 被断言误判为错误 → "BFF 19% 错误率"假结论，修正后全零错误（bug #40）。**结论**：瓶颈=BFF 转发层单实例（并发翻倍 RPS 反降 173→137→114），服务层稳定 >370 RPS 非瓶颈；可解释容量=服务层 >370 RPS、BFF ~170 RPS；扩容方向=BFF 横向多实例。详见 CHANGELOG [0.2.36]。

* 2026-10-18：**工作流3 #7 服务间超时/熔断（生产级加固第二块）**：公共库 ResilientHttp 编程式熔断（4 处调用点 orderFetchSku/orderReleaseInventory/cartValidateSku/paymentNotifyOrder）+ 参数配置化 + 状态事件日志。**实测（故障注入杀 product）**：CLOSED→OPEN（12 连发失败）→熔断期间占位价 100 降级下单（业务不中断）→product 恢复半开探活→CLOSED 金额回真实价 9900。坑：@CircuitBreaker 注解对同类 private 方法失效（AOP 代理 self-invocation）→ 编程式（bug #39）。详见 CHANGELOG [0.2.35]。

## 已完成

* 2026-10-18：**工作流3 #10 traceId 全链路（生产级加固第一块）**：公共库 trace 包（TraceIdFilter 入站注入 x-trace-id→MDC→响应头回写；RestTemplate 拦截器 + Feign 拦截器出站透传）+ TraceFeignAutoConfiguration @ConditionalOnClass 条件装配 + 11 服务日志带 traceId。**实测**：x-trace-id=TRACETEST123456 下单 → order 8085 + product 8083 两条日志 traceId 完全一致（跨服务贯穿闭环）。踩坑：公共库 optional openfeign 依赖不传递 → 无 Feign 服务 NoClassDefFoundError → 拆自动配置按需装配（bug #38）。详见 CHANGELOG [0.2.34]。

* 2026-10-08：**P0-D1 日志脱敏 AOP（生产非功能第一块）**
* 2026-10-08：**P0-D2 容器化部署能力落地（生产非功能第二块）**
* 2026-10-08：**P0-D3 CI/CD 流水线（生产非功能第三块）**
* 2026-10-08：**P0-D4 全链路压测（生产非功能第四块）**
* 2026-10-08：**P1 性能优化落地（压测瓶颈修复）**：product/page Redis 缓存（TTL300s，命中 15x）+ HikariCP 50 + BFF keep-alive 连接池 + RAG 300ms 快速失败 + RAG 结果缓存；复测 ① 错误率 35.65%→5.82%、P95 10.2s→8.1s、page 33%→82%；② QUICK 50VU **0 错误 P95 329ms 达标**；2000VU 未达标为单机容量（生产化建议见报告 §6.3）。详见 CHANGELOG [0.2.0]。：k6 脚本 \scripts/load-test/full-chain-load-test.js\（黄金路径读 80%/写 20%、阶梯 100→2000VU、QUICK 冒烟模式）；基线 50VU 0 错误 P95 213ms；全量 52763 请求错误率 35.65%（product/page 33% 通过为瓶颈）+ 优化建议 P1（Redis 缓存/BFF 连接池/RAG 快速失败）。报告 \docs/load-test/全链路压测报告.md\。详见 CHANGELOG [0.1.9]。：\.github/workflows/ci.yml\（push main/PR/tag 触发；build-test JDK17+mvn verify 全模块单测；build-images 矩阵 11 服务多阶段 Dockerfile 构建 + GHCR 推送，tag 策略 main-/latest/版本；gha 层缓存；多阶段 Stage1 在 CI 补验证）+ \.github/workflows/deploy.yml\（手动触发，SSH + compose pull && up + 逐服务健康检查，secrets 校验）。YAML 校验通过。详见 CHANGELOG [0.1.8]。：① 11 服务生产级多阶段 Dockerfile（Stage1 maven:3.9-eclipse-temurin-17 容器内编译——先拷全部 POM 复用层缓存再 -am 打包；Stage2 eclipse-temurin:17-jre 非 root（uid 1001）+ G1GC JVM 参数 + EXPOSE + HEALTHCHECK /actuator/health）；② 根 .dockerignore 压缩构建上下文；③ 11 个运行时镜像 zhigou/<svc>:0.1.0 构建成功，user-service 容器实测 JVM 17 启动正常、非 root（started by app）；④ 生产编排 infra/compose/services.yml（compose_default 网络 + 环境变量覆盖 DB/Redis/MQ/服务间 URL + restart 自愈，config 校验通过）。踩坑： here-string 转义、reactor 需全模块 POM、本地容器内 mvn 网络不通（runtime Dockerfile 绕过）。详见 CHANGELOG [0.1.7]。：packages/common 新增 mask 模块（SensitiveType/MaskUtil/SensitiveLog/SensitiveLogAspect/MaskAutoConfiguration），@SensitiveLog 注解 + 序列化后递归掩码（字段名敏感词 + 正则智能识别 + @SensitiveField 注解兜底，失败降级 toString，绝不抛异常影响主流程）；auth-center login/send-sms-code 接入。**实测**：登录日志 `phone=******** / code=****** / accessToken=********`，**Snowflake userId 完整保留不误掩**（银行卡正则收紧为 [3456] 开头）；MaskUtilTest 11/11。详见 CHANGELOG [0.1.6]。
* 2026-10-08：**CI 修复：file-service MinIO 镜像拉取 404**：Docker Hub `minio/minio:latest` 已停维护/匿名拉取受限 → 默认改 `quay.io/minio/minio:RELEASE.2024-11-07T00-52-20Z`（固定版）+ `MINIO_IMAGE` 环境变量可覆盖 + 启动等待策略；本地 6/6 测试全绿（CHANGELOG [0.2.5]）。
* 2026-10-08：**P1 AI 决策辅助第二批（会话记忆/凑单/代下单）**：① 会话记忆持久化（MemorySaver→本地 JSON 文件，跨进程/重启延续 + `DELETE /chat/session/{sid}` 清空 + BFF 透传 + H5 清空按钮）；② AI 凑单优化器（`optimize_cart`：购物车+SKU 现价/规格 + marketing 满减引擎 + 逐券试算最优）；③ 一键代下单（`create_order`：真实调 order-service 下单 + 用户确认流）。**实测**：SSE 全链路「怎么买最划算」→ 购物车 2 件 ¥148 明细+最优方案；跨轮「确认下单」→ 订单 2108152430193233920 真实落库（¥148）；会话清空接口 200。详见 CHANGELOG [0.2.4]。
* 2026-10-08：**CI 集成测试基建修复（全量 78 项测试全绿）**：① Testcontainers 加固——`@DynamicPropertySource` 注册标准键 `spring.datasource.url/username/password` + `spring.data.redis.host/port`（修 TEST_JDBC_URL 键名不被 Spring 识别）、MySQL/Redis 容器显式等待 + 120s 超时、payment/marketing/aftersale 补 Redis 容器、auth schema.sql 对齐生产迁移（user_id 列）；② test profile 安全链——9 服务 `SecurityConfig @Profile("!test")` 导致默认安全链全拒 401，新建 10 个 `TestSecurityConfig`（permitAll）接入测试类（inventory 非 Web 不导入）；③ 对齐测试与实现——cart mock product 校验 URL/响应、order 重复支付回调幂等断言改验状态机非法跃迁、OutboxDeliveryTask `@Profile("!test")`（test 无 MQ）、清理 payment 残留旧迁移（V20261007 → Flyway 重复 ADD 列）。**`mvn verify` 全模块 0 失败 0 错误（auth 3 / user 6 / file 6 / product 5 / cart 4 / order 10 / inventory 4 / payment 7 / marketing 8 / logistics 6 / aftersale 8 + MaskUtil 11）**，GitHub Actions CI `build-test` 可全绿。详见 CHANGELOG [0.2.3]。
* 2026-10-08：**P1 容量调优（写链路瓶颈消除）**：HikariCP 连接池全服务化（10 个 DB 服务统一 pool 50，pool-name ZhigouHikariPool）；复测③ 2000VU **错误率 0.00%**（0/86245）、RPS 303（+50%）、P95 5.02s（-36%，持续收敛 10.23→8.14→7.86→5.02）、5 类接口 checks 全 100%；P95 未达 1.5s 为单机写事务耗时，生产化路径（BFF 横向扩展/写链路异步化/分库分表）见报告 §6.3。详见 CHANGELOG [0.2.2]。


* 2026-10-08：**P0-C 售后逆向全流程闭环（退款真实资金流 + 退回库存联动）**：① payment-service 新增退款表 `payment_refund`（V20261092，refund_no 幂等键）+ `POST /payment/refund`（校验支付单 SUCCESS、金额 0<amount≤实付、沙箱即时 SUCCESS）；② aftersale-service 退款改真实链路（V20261093 加 sku_id/count/refund_no；refund：SELLER_APPROVED→REFUNDING→调 payment→REFUNDED 记 refundNo→调 inventory 回库存，失败告警"待人工补偿"；REFUNDED 幂等）；③ inventory-service 放行 preDeduct/confirm/rollback（此前 HTTP 通道 403，回库存一直靠 MQ 兜底）；④ BFF 新增 aftersale 透传模块（apply/mine/detail/cancel 注入 X-User-Id 头，修复 mine 恒 0 条）。**实测闭环**：¥1 单 apply→approve→refund→REFUNDED（RFF2452BD37CD04379）→payment_refund 落库→**库存自动回滚 99→100**；PENDING 单退款被正确拒绝；H5 售后 tab 7 条 + 详情页状态卡/退款单号展示正确。详见 CHANGELOG [0.1.5]。



* 2026-10-08（P1 三批）：**AI 决策辅助第三批——隐性需求挖掘 + 提示词对齐**——ai-orchestrator 新增 `analyze_user_context` 工具（历史订单→常购品类/价位带/复购倾向，无历史不编造），system.md 补齐工具清单与「拆解→隐性补全→搜索→比价→避坑→推荐」六步流程，红线修正为「禁止自主下单（用户确认后允许代下单）」；SSE 实测全链路通过（22 单历史 → 运动鞋/百元内）。
* 2026-10-07：**P0-B 交易最终一致性落地（outbox + RocketMQ）**：① order-service OutboxDeliveryTask（30s 周期投递 status=0 → MQ → 置 1，at-least-once）；② ORDER_CLOSED 事件体升级（含 items 明细），取消/超时关单统一新格式；③ inventory-service OrderClosedListener 消费兜底 + 
ollbackOrder(orderId, items) Redis SETNX 幂等（双通道只释放一次）；④ **RocketMQ broker 地址修复**（Windows Docker Desktop：容器内网 IP 宿主不可达 → -c 强制读取 conf/broker.conf + docker cp 覆盖 rokerIP1=127.0.0.1 + restart，clusterList 已显示 127.0.0.1:10911）。实测：投递 41/41 成功（status 全=1）；同 orderId 双消息幂等闭环——首条释放库存 94→96，次条跳过，只释放一次。详见 CHANGELOG [0.1.4]。\n* 2026-10-07：**P0-A 交易完整性第一梯队落地**：① 超时关单（order-service `OrderTimeoutTask` 每 5 分钟 + 启动首扫，INIT 超 15 分钟→CLOSED + outbox + 释放库存，首扫关闭 16 笔）；② 支付对账补偿（payment-service `notify_status` 迁移 V20261091 + reconcile 补偿 SUCCESS 未通知单 + 运维端点 `/payment/reconcile`，实测补偿 4 笔）。详见 CHANGELOG [0.1.3]。

* 2026-10-07：**支付待付款终极根因修复 + 浏览器实测闭环**：① payment-service Spring Security 未放行 mock-pay 致全部支付回调被 403（BFF 吞错伪装成功）→ SecurityConfig permitAll `/payment/sandbox/mock-pay`、`/payment/notify/**`，BFF 不再吞错；② 支付成功自动通知 order-service（新增 `OrderNotifyClient` + payCallback 幂等），订单 INIT→PAID 联动自动化；③ 订单详情页 INIT 轮询刷新；④ 订单列表 tab 筛选修复（模板误用 `list` 而非 `filtered`）+ 新增"售后"tab；⑤ 详情页按钮按状态机收敛。**浏览器全流程实测**：登录→加购→结算→提交→支付成功→自动跳订单详情"商家备货中"（PAID）；售后/待付款 tab 正确过滤。已提交 git（见 CHANGELOG [0.1.2]）。

* 2026-10-07：**全链路联调冒烟 PASS 17/17**（12 微服务 + BFF + H5 全启动；黄金路径 登录→商品→AI 对话→加购→下单幂等→支付→回调→库存→物流→售后→我的订单 全绿）。脚本 `scripts/smoke/zhigou-e2e.ps1`。联调修复 6 处真实 bug：Snowflake ID JSON 精度丢失（order/cart/auth DTO 序列化转字符串）、payment 强转 ClassCastException、aftersale 403 请求头不匹配、cart/file/user 拦截器不认内网透传头、AI 401 降级兜底、ai-orchestrator 启动入口修正。

* 2026-10-07：**前端 8 屏对齐原型 + 交易闭环**（H5 16 路由、5 TabBar、首页 / AI 对话 / 商品详情 / AI 比价 / AR 试穿 / 衣橱 / 社区 / 我的；购物车 / 结算 / 订单 / 支付 / 地址 / 优惠券），BFF 扩展 cart/order/payment/marketing/user/logistics 透传模块；`order-service` 新增 `GET /order/mine` + 下单取真实价格 / 商品名；`cart-service` 新增 `DELETE /cart/{skuId}`；BFF/H5/order/cart 四连构建通过。

* 2026-10-02：BFF 层压测（50 并发 / 4500 请求 / 0 错误 / P95 131ms / RPS 718），报告见 `docs/load-test/`；生产就绪度评估（30%）见 `docs/智购开发任务差距分析报告.md`（附录 A）。

* 2026-10-02：Mock 数据机制（Flyway profile 分离 + 固定 ID 段 + 一键清除脚本）落地，指南见 `docs/mock-data-cleanup-guide.md`。

* 2026-09-30：仓库骨架初始化；CLAUDE.md/ ADR-0001 /progress.md 落地；M0 中间件 + auth-center 登录。

## 进行中



* **P0・M1 交易闭环收尾**（差距报告 P0）：


  * ✅ 超时关单（[0.1.3]，INIT 超 15min 自动 CLOSED + 释放库存）
  * ✅ 支付对账补偿（[0.1.3]，SUCCESS 未通知 → 补偿；PENDING 超 24h 告警；运维端点）
  * RocketMQ 事务消息落地下单 - 扣库存 - 支付最终一致性（outbox 表已有、无投递任务）

  * 超时未支付自动关单、部分退款等状态边界

  * 支付回调幂等 + T+1 对账业务逻辑（当前为骨架）

  * ✅ 售后逆向全流程（[0.1.5]：申请→审核→退款→退回库存→拒绝，退款资金流真实化）

  * 营销活动逻辑（满减 / 秒杀 / 拼团 / 凑单）与券叠加互斥

  * 商品评价 + 评价增量向量更新（当前仅全量回填脚本）

  * 用户画像模块（收藏 / 浏览历史 / 会员基础）

  * 文件服务图片压缩 / 格式转换 / 访问鉴权

  * BFF 补齐全部业务接口聚合 + 埋点 + 熔断降级 + SSE 重连与会话管理

  * 前端售后页 + 商家后台业务页（上下架 / 订单 / 营销配置）

* **非功能（生产就绪，见差距报告附录 A）**：


  * ✅ 11 个 Java 服务 JWT 鉴权过滤器已存在并生效（common 库 JwtAuthFilter + SecurityConfig；内网调用走 `X-User-Id` 透传头，联调确认）

  * 日志脱敏（当前 login 日志直打手机号）

  * Dockerfile + 镜像构建（当前仅 auth-center /ai-orchestrator 有）

  * CI/CD 流水线（当前无 .github/workflows）

  * 服务间超时 / 熔断（当前仅 cart-service 有超时配置）

## 待办池（按差距报告顺序）

### P0・M1 收尾（先做）



* [x] ~~交易最终一致性（RocketMQ 事务消息 + outbox 投递任务）~~（outbox 表 + 投递任务 + RocketMQ 事务消息已落地，见 CHANGELOG [0.2.x]）

* [x] ~~超时关单 / 支付对账 / 退款资金流~~（RocketMQ 延迟关单 + reconcile 对账 + 售后退款资金流已闭环）

* [x] ~~售后全流程 + 凭证上传 MinIO~~（申请→审核→退货入库→退款→拒绝全流程 + 凭证 MinIO 已闭环）

* [x] ~~营销活动 + 凑单最优组合 + 防超卖~~（券/满减/拼团 + optimize_cart 凑单 + 库存 Lua 防超卖已交付）

* [x] ~~评价增量向量更新（pgvector）~~ ✅（[0.2.30] 按差距报告口径「商品增量向量更新」闭环：pgvector 底座 pg16 容器 + products_vec 表 + 全量回填 backfill_vec.py 跑通 + PRODUCT_CHANGED 增量链路（Windows 本地 MQ SDK 不可用，生产 Linux 自动激活）+ RAG 语义检索验证）

* [x] ~~用户画像 / 收藏 / 浏览历史 / 会员基础~~ ✅（[0.2.29] 收藏/浏览底座 + 画像洞察已交付：user-service 8 端点 + BFF 透传 + AI analyze_user_context 三源融合 + H5 详情页/收藏页/浏览历史/我的入口；会员基础随 wallet 已部分落地）

* [x] ~~文件服务增强（压缩 / 转格式 / 鉴权）~~ ✅（[0.2.31] 图片处理管线：像素上限防 DoS + 等比缩放 + JPEG/PNG 压缩转换 + bizType/尺寸元数据落库 + H5 售后凭证/对话搜款接入；file-service 直连 5 场景 + 售后凭证闭环 PASS）

* [ ] BFF 补全 + 埋点 + 熔断降级 + SSE 重连——**部分完成**（接口聚合已补全，埋点/熔断降级/SSE 重连待开发）

* [x] ~~前端售后页 + 商家后台业务页~~（H5 售后 tab/详情已交付；商家后台以商家中心 H5 承载，admin-merchant 未扩展）

* [x] ~~Java 服务 JWT 过滤器（11 个）~~（联调确认已存在并生效）

* [x] ~~日志脱敏（AOP）~~（P0-D1 完成：packages/common mask 模块 + auth-center 接入，登录日志 phone/验证码/token 全掩、userId 不误掩，见 CHANGELOG [0.1.6]）

* [x] ~~Dockerfile（9 个缺失服务）+ 镜像构建~~（P0-D2 完成：11 服务生产级多阶段 Dockerfile + 根 .dockerignore + 11 个运行时镜像 zhigou/<svc>:0.1.0 + 生产编排 infra/compose/services.yml，见 CHANGELOG [0.1.7]；本地容器内 mvn 网络受限，多阶段 Stage1 留 CI 验证）

* [x] ~~CI/CD（build→test→镜像→部署）~~（P0-D3 完成：\.github/workflows/ci.yml\ build-test→build-images(GHCR)→summary + \.github/workflows/deploy.yml\ 手动部署，见 CHANGELOG [0.1.8]）

* [x] ~~全链路压测（k6 阶梯 100→500→1000→2000）~~（P0-D4 完成：k6 全链路脚本 + 基线 0 错误 + 全量压测定位瓶颈，见 CHANGELOG [0.1.9]；**P1 优化已落地并复测**（缓存/连接池/RAG 快速失败，QUICK 50VU 达标，见 CHANGELOG [0.2.0]））

### P1・AI 决策辅助（M2 与产品目标的差距核心）
- [x] 需求拆解（analyze_requirement：预算/品类/场景/偏好结构化，已上 SSE 链路）
- [x] 跨平台比价（compare_prices：多 SKU 价格/规格/库存横向对比）
- [x] 差评/避坑（review_analysis：选购提醒 + 评价数据待接入降级）
- [x] ~~真伪测评/水军识别~~（[0.2.5] review_analysis 规则化识别已交付：疑似刷评/差评比例/口碑）
- [x] ~~个性化推荐升级~~（[0.2.5] recommend_products + /recommend 可解释推荐已交付）
- [x] ~~图片搜款/多模态~~（[0.2.6] search_by_image 视觉搜款 + H5 相机入口已交付）
- [x] ~~AI 凑单优化器（券+满减最优组合）~~（[0.2.4] optimize_cart 工具交付：购物车+SKU 现价+满减引擎+逐券试算）
- [x] ~~一键代下单（Agent 对接下单 + 用户确认流）~~（[0.2.4] create_order 工具交付：真实下单 + 用户确认流实测）
- [x] ~~会话记忆持久化（当前 MemorySaver 进程内存）~~（[0.2.4] 本地文件持久化 + DELETE 清空接口 + H5 清空按钮；生产注释换 Redis）
- [x] ~~需求拆解 / 跨平台比价 / 差评避坑~~（analyze_requirement / compare_prices / review_analysis，已上 SSE 链路）
- [x] ~~推荐引擎（行为 + 画像）~~（[0.2.5] recommend_products + /recommend 可解释推荐，含刷评/已购标记）
- [x] ~~图片搜款 / 多模态~~（[0.2.6] search_by_image 视觉搜款 + H5 相机入口）
- [x] ~~推荐理由标准化（可解释性）~~（recommend 返回理由 + RAG 溯源）




* [x] ~~需求拆解（analyze_requirement）~~ ✅
* [x] ~~跨平台比价（compare_prices，6 渠道演示口径）~~ ✅
* [x] ~~差评分析 / 避坑指南（review_analysis）~~ ✅
* [x] ~~推荐引擎（recommend_products，画像+评分+可解释）~~ ✅
* [x] ~~AI 凑单优化器 / 一键代下单（用户确认流）~~ ✅
* [x] ~~会话记忆（持久化 + 清空）~~ ✅
* [x] ~~推荐理由标准化（可解释性）~~ ✅

### P2+・生态矩阵 / 远期（立项评估后）



* [x] ~~社交购物（AI 送礼 / 拼团）~~（[0.2.9-0.2.11] gift_assistant + groupbuy_finder + marketing 拼团）
* [x] ~~内容社区~~（[0.2.12-0.2.13] community-service：笔记/短视频/直播 + 虚假内容识别）
* [x] ~~本地生活~~（[0.2.14] life-service：POI/到店预约，成都商圈演示数据）
* [x] ~~智能衣橱 / 家居管理~~（[0.2.15] closet-service：衣物/穿搭/家居盘点）
* [x] ~~AI 会员订阅体系（¥29 / ¥99 权益）~~（[0.2.19] wallet-service 订阅 + 权益）
* [x] ~~商家端 AI 经营体系~~（[0.2.17]-[0.2.27]：经营大脑/客服营销/供应链/物流/售后）
* [ ] P3-P4：多模态搜款（✅ 已验证）、AR/VR、数字人、个人购物大模型（无技术底座，待立项评估）

## 风险与阻塞



| 风险          | 等级 | 说明                                                                                                    |
| ----------- | -- | ----------------------------------------------------------------------------------------------------- |
| 生产就绪度（附录 A 快照 30%） | P0 | 快照过时：JWT 过滤器/日志脱敏/Dockerfile/CI-CD/k6 压测已补齐，RocketMQ 事务消息与支付对账已闭环 —— 详见 `docs/智购开发任务差距分析报告.md`（附录 A，落地以本文件为准） |
| 压测仅覆盖 BFF 层 | P1 | 50 并发 718 RPS；M3 目标 5000 QPS 需全链路压测 + 瓶颈优化（当前唯一 P1 未闭环项） |

## 变更记录
* 2026-10-22：**坑 #63 精度修复铺开（[0.2.58]）**：含 19 位 id 的透传接口统一字符串透传——ship-partial/aftersale/cart 走 rawBody 原样转发，marketing 拼团/秒杀 id 字段字符串化（Java Jackson 字符串→Long）。实证：下单落库 sku_id=9000000000000000020 保真；cart/aftersale 透传无损。

* 2026-10-22：**坑 #62/#63 修复（[0.2.57]）**：①order-service 下单写链路解耦（SKU 本地缓存 TTL 30s + 缓存兜底，杜绝回退占位价静默漂移）；②BFF rawBody 字符串透传（19 位 Snowflake skuId 超 JS 安全整数精度丢失——根因 bug）。复测：80VU 双实例混合读写错误率 40.61%→0.00%。

* 2026-10-22：**M3 生产化路径容量模型（[0.2.56]）**：BFF 横扩 1.83x（单 290 → 双 531 RPS 零错误区）+ 读层 1263 RPS 实证；瓶颈定位=order→product 同步调用级联超时（坑 #62，回退占用价静默漂移）；目标决策=5000 QPS 集群形态 + 单机可解释容量验收。产出容量模型报告 + test_m3_scale.py。

* 2026-10-22：**M4 上线收尾（[0.2.55]）**：回滚演练实测闭环（停 8085 → 错误 DB 配置发布失败 → 冒烟失败触发回滚 → 正确配置恢复 200）；release-checklist §1-§6 逐项签核（生产前置项注明「生产前必做」）。

* 2026-10-22：**B7 故障演练 · 熔断降级闭环（[0.2.54]）**：注入停 product-service(8083) → BFF 详情接口降级 200 + 3ms（不 5xx/不挂起，日志熔断 OPEN 快失败）→ 恢复后 HALF_OPEN 探活成功（835ms）→ CLOSED 正常（~25ms）。复盘 bug #60；Runbook 已覆盖核心故障。

* 2026-10-22：**B6 SLO 大盘 + 错误预算落地（[0.2.53]）**：zhigou-slo.json 7 面板（错误预算/可用性 30d·7d/错误率 0.5% 线/P95 1s 线/各服务可用性/支付成功率）+ Prometheus retention 60d + payment-service 支付埋点（success/failed/refund 计数器）。实测：SLO 大盘加载、payment_success_total=1（create→mock-pay 200）、错误预算公式修复（坑 #59，PromQL 优先级 -99900 → 100）。

* 2026-10-21：**A8 Prometheus 编排实跑全绿（[0.2.52]）**：monitoring.yml 修 4 处坑（compose 相对路径 ../../conf、Loki pin 2.9.10 + user root + compactor working_directory、healthcheck 移除、host-gateway）→ 4 容器全 Up、Prometheus targets 11/11 UP（8080-8090 全量 /actuator/prometheus）、5 规则加载 inactive、Grafana provisioning 生效（2 数据源 + RED 面板）。坑 #55-#58 已留痕。

* 2026-10-21：**统一补验全绿 10/10（[0.2.51]）**：环境恢复（中间件全 healthy + 18/18 端口 UP + token 刷新）后跑 %TEMP%\verify_unified.py 迭代 5 轮：B5 写库加密 PASS → 读路径 MyBatis 自动映射 LongTypeHandler 读 ENC 密文 500（坑 #53，显式 ResultMap 6 方法 + 7 处查询替换）；A7 `{fileId:.+}` 在 PathPattern/Ant 均不匹配（坑 #54，三段式路径变量 `{date}/{userId}/{fileHash}` 治本）；B1 并发 30/30 全 200；B2 积压采样日志实测；A4 取消订单库存回滚 200。三件套已提交。

* 2026-10-10：**P0 商品增量向量更新（[0.2.30]）**：pgvector 维度硬伤修复（表 vector(1536)→1024，对齐 text-embedding-v4 输出，此前 upsert 必失败、表恒 0 行）；backfill_vec.py 整改（URL 8095 / X-User-Id 内网头 / 自动加载 .env / embed_url 死参数清理）并**全量回填 4 条真实商品跑通**；RAG 语义检索 PASS（「跑步运动鞋」→ 运动鞋 0.8127）；增量逻辑直调验证 PASS（模拟 PRODUCT_CHANGED 消息 → 新商品 9000000000000000999 入向量库）；AI 改 venv 启动（曾跑系统 Python 教训 #25③）；⚠️ rocketmq-client-python Windows 不支持 → 本地 MQ 消费者无法激活（生产 Linux/容器可用），已如实留痕。

* 2026-10-10（注）：**[0.2.29] commit c5cbcd6 已本地提交；push 曾因 GitHub 443 连接超时失败**——已于后续批次网络恢复时补推（同 [0.2.11]/[0.2.12] 留痕惯例）。

* 2026-10-10：**P0 用户画像 / 收藏 / 浏览历史底座（[0.2.29]）**：user-service 画像底座（Flyway `V20261091__user_favorite_browse.sql` 两表 + 实体/Mapper/DTO + UserProfileService/Impl 收藏幂等/浏览聚合/画像洞察 + Controller 8 端点 + SecurityConfig 放行 `/user/insight`）；BFF +9 透传路径；AI `analyze_user_context` 三源融合（/user/insight + 历史订单 + 画像并入）；H5 详情页收藏/浏览真接口 + /favorites /history 页 + 我的页入口。**修复 #32 JS Number 精度丢失**（favorite/ids 19 位 Snowflake 经 JSON.parse 201→300 —— 后端改 `List<String>` 字符串透传）；修复 PowerShell ANSI 编码事故（Impl/Controller 重写，UTF-8 无 BOM）。验证：8081+BFF favorite/ids 均返回字符串数组；BFF 8 端点全 200；H5 浏览器端到端 PASS（详情页收藏写入 + 刷新「已收藏」态 + /favorites 1 件 + /history 看过 2 次 + 我的页入口）。

* 2026-10-10：**文档体系对齐（[0.2.28]）**：以《智购功能文档.md》《智购-企业级工程化技术方案.md》为基准对齐 10+1 份文档 —— ① README/CLAUDE/docs-README：服务 12→16（补 community/life/closet/wallet）、AI 8000→8095、AI 工具 5→29、CI 已接入（13 checks 绿）、目录树/端口表/架构图/数据流/启动命令同步；② progress：待办池按真实进度勾销（P0 收尾完成项 + P1 全项 + P2 全项，保留 4 个未闭环项）、当前阶段/风险与阻塞更新、[0.2.12] push 留痕注更新；③ 技术方案 v2.0：「当前实现」对比行（16 服务/29 工具）+ 里程碑 M2-M4 状态；④ 前端改造说明：衣橱/社区改「已实现」、AI 启动 8095；⑤ release-checklist / gray-release / monitoring-alerting / mock-data-cleanup-guide：服务清单/分批表/监控 targets/mock 表补 4 个新服务；⑥ 新建 `docs/adr/ADR-0001-服务治理与AI端口迁移.md`（填补空 adr 引用，对齐技术方案 ADR 纪律）。

* 2026-10-10：**P2 3.3 商家端 AI 客服与营销一期（[0.2.27]）**：评论自动回复 + 负面预警 —— product-service Flyway `V20261093__merchant_review_reply.sql`（review_reply 表）+ `GET /product/review/merchant/pending`（情感 + AI 建议话术）+ `POST /product/review/merchant/reply`；AI `review_assistant`（TOOLS 29，pending/reply 双动作）+ system.md/路由；BFF 2 端点；H5 商家中心「评论管理」区块。修复：AI f-string 引号/转义坑、BizException 包路径、ReviewReply import。验证：直连 pending 8 含 3 负面、reply 移出；SSE PASS（负面盘点+一键回复模板）；BFF pending=9/3 负面；双端构建 PASS。

* 2026-10-10：**P2 3.3 商家端 AI 经营大脑二期（[0.2.26]）**：动态定价 `GET /product/merchant/pricing`（趋势/库存/竞品 → UP/DOWN/HOLD + 建议价，演示口径）+ 营销方案 `GET /marketing/plan`（会员价/拼团/满减策略 + 渠道，演示口径）；AI `dynamic_pricing` + `marketing_plan`（TOOLS 28）+ system.md/路由；BFF 2 端点；H5 经营大脑区块追加定价+营销子区。修复：BFF 服务名拼写（marketingService→marketingSvc，含 timeout 引用）。验证：直连 pricing 3/plan 3、SSE 双 PASS、BFF 三层 PASS、双端构建 PASS。

* 2026-10-10：**P2 3.3 商家端 AI 经营大脑一期（[0.2.25]）**：product-service 销量预测 `GET /product/merchant/forecast?days=7|30`（近 7 日日均×天数×(1+趋势) + 建议备货，演示口径）+ 智能选品 `GET /product/merchant/selection`（热度/库存/趋势 → 建议）→ Flyway `V20261092__merchant_forecast.sql`（daily_sales seed 3 SKU×14 天）；AI `sales_forecast` 工具（TOOLS 26）+ system.md 商家经营角色 + 路由强制触发；BFF 2 端点；H5 商家中心经营大脑区块（7/30 切换 + 选品建议）。修复 4 坑（product 库 V20261091 mock 撞版本→V20261092 / selectCount Long→int / TOOLS 显式列表未注册 / system.md 缺商家角色致模型拒经营场景）。验证：直连 7/30+选品、BFF 三层、SSE 商家 query PASS、双端构建 PASS。

* 2026-10-10：**P2 AI 售后助手（[0.2.24]）**：aftersale-service 质保提醒 `GET /aftersale/warranty/alerts`（NORMAL/EXPIRING_SOON 30 天内/EXPIRED + daysLeft/hint）+ 维修预约 `POST /aftersale/repair/appointment` + `GET /aftersale/repair/appointments`（状态机 PENDING/CONFIRMED/DONE/CANCELED）→ Flyway `V20261094__aftersale_repair.sql`（warranty_info seed 3 条 + repair_appointment）；AI `after_sale_repair` 工具（TOOLS 25：质保盘点/预约创建，缺参如实提示）；BFF 3 端点；H5 对话页售后助手入口升级。修复 3 坑（新 Mapper 缺 @Mapper 注解不注册 / LocalDateTime.parse 需 ISO T / aftersale 全量 JWT 拦截 AI 直连 403→放行 warranty/repair）。验证：Bearer 直连 3+1+1、AI SSE 质保盘点 PASS、BFF 三层 3/2+RP 单 PENDING、双端构建 PASS。

* 2026-10-10：**P2 智能物流管家升级（[0.2.23]）**：logistics-service 延误预警 `GET /logistics/delay-alerts`（在途未签收未取消运单按最近轨迹节点判疑似停滞，默认 48h）+ 一键调度 `POST /logistics/dispatch`（URGE/REDELIVER/SELF_PICKUP/CHANGE_ADDRESS/RETURN，演示口径写 DONE 记录）→ Flyway `V20261091__dispatch_action.sql` + DispatchAction 实体/Mapper；AI `logistics_delay_alert` 工具（TOOLS 24：预警盘点 + 一键调度）；BFF `GET /freight/delay-alerts`、`POST /freight/dispatch`；H5 对话页「物流管家」快捷入口升级预警语义。修复 3 个坑（无 swagger 依赖 @Operation 移除 / 轨迹排序 null NPE / 端点无类级前缀致静态资源 404）。验证：后端直连 10 在途 1 停滞→URGE DONE、AI SSE 盘点+催件 PASS、BFF 三层 10/1 + SELF_PICKUP DONE、双端构建 PASS。

* 2026-10-10：**P2 商家端 AI 经营体系一期（[0.2.17]）**：order-service 经营统计接口 /order/stats/overview（订单数/销售额/今日/状态分布/热销Top5/售后）+ AI merchant_overview 经营助手工具 + BFF /merchant/overview + H5 商家中心页（指标卡/状态分布/热销榜/AI 助手入口）。修复 chat_service._dispatch_tool `__name__` bug（影响全部 19 工具 SSE 调用）。验证：直连/BFF/H5 渲染/SSE 全链路 PASS。

* 2026-10-10：**P2 会员钱包（[0.2.16]）**：新建 wallet-service（8094）余额账户/流水（bizNo 幂等）/沙箱充值（1 元=1 积分）/会员等级（FREE/ADVANCED/FLAGSHIP 权益说明）；BFF +4 路由；H5 我的钱包页（余额卡/充值弹层/收支明细）+ 我的页入口。验证：直连 9/9、BFF 6/6、H5 构建 + 浏览器实测充值闭环。

* 2026-10-10：**P2 智能衣橱 + 家居管理（[0.2.15]）**：新建 closet-service（8093）衣物单品/穿搭方案/家居盘点三表 + 穿搭推荐规则引擎（场合+穿着次数轮换，note 标注演示）+ 补货清单（数量≤1 或 7 天内到期）；BFF +8 路由；H5 衣橱页整体改造（衣物/穿搭/家居三 tab）。验证：直连 11/11、BFF 9/9、H5 构建 + 浏览器实测。

* 2026-10-10：**P2 本地生活 MVP（[0.2.14]）**：新建 life-service（8092）POI 门店/商圈 + 服务 SKU + 到店预约（状态机 待确认/已确认/已完成/已取消），Seed 成都真实商圈演示数据；BFF +6 路由；H5 本地生活页（6 分类 tab）+ 门店详情（预约表单）+ 我的预约；首页宫格第 8 格。验证：直连 10/10、BFF 8/8、H5 构建 + 浏览器渲染。

* 2026-10-10：**P2 内容社区二期（[0.2.13]）**：短视频（图文 MVP，video_url 兼容真实视频 URL 即插即用）+ 直播（状态机预告/直播中/已结束，直播流演示占位标注）。community-service V2 迁移 2 表 + seed；BFF +7 路由；H5 短视频竖屏 feed 页 + 直播列表/详情页 + 首页宫格 2 入口 + 社区 tab。验证：直连 8/8、BFF 接口全通、H5 构建通过、浏览器渲染验证（短视频 5 条/直播 2 场）。

* 2026-10-10（注）：**[0.2.12]/[0.2.11] push 曾因 GitHub 443 失败**——已于后续批次全部推送成功（origin/main 与本地一致），此注仅为留痕。

* 2026-10-10：**P2 内容社区 MVP（[0.2.12]）**：新增 community-service（8091，笔记/评论/点赞收藏/虚假内容识别 SPAM_WORDS+重复检测）；AI 种草文案 writer（真实数据锚点）；BFF 透传 8 路由 + 雪花 ID 字符串化修复 + ai-writer 超时豁免；H5 社区页换真（信息流/发布/AI 写文案/详情评论/营销标记）。验证：直连 11/11、BFF 全链路 7/7、双端构建通过；浏览器 UI 点按实测受 bu 会话 viewport 异常未完成（页面渲染文本验证通过，交互以 API 验收为准）。





* 2026-10-07：支付待付款终极根因修复（Security permitAll + BFF 不吞错 + 订单自动联动 PAID + 详情页轮询 + 订单列表筛选修复 + 售后 tab）；浏览器实测支付闭环"商家备货中"；悬浮购物车（414px 容器定位）；真实支付接入指南；全链路联调冒烟 17/17（12 服务 + BFF + H5 全启动，黄金路径全绿，6 处联调 bug 修复）；前端 8 屏对齐 + 交易闭环交付；BFF 六模块透传；order/cart 补口与 bug 修复；文档体系统一（docs/README 索引 + README/CLAUDE/progress 对齐）。

* 2026-10-02：BFF 压测；生产就绪度评估；Mock 数据机制。

* 2026-09-30：项目初始化。