# 智购・项目进度追踪

> **唯一进度真相**：每完成一个可验证单元，更新本文档 + `CHANGELOG.md`；涉及接口 / 端口变更时同步 `README.md` 与 `docs/README.md` 索引。
> 状态：✅ 已完成 / 🚧 进行中 / ⬜ 未开始 / ⚠️ 有风险
> 与目标态的差距清单与执行顺序见 `docs/智购开发任务差距分析报告.md`。

## 2026-10-23 · [0.2.82] 精度闭环收口：读路径 19 位 id 字符串化（bug #63 根治）
- **修复**：响应 DTO/Entity 的 Long id 语义字段全量 `@JsonSerialize(ToStringSerializer)`——39 文件 61 字段（含 Shipment/CalculateResponse 两处拆行避开金额字段）+ 17 文件补 import
- **纪律**：请求 DTO 不动；金额字段永不注解；id 语义 = id + 以 Id/id/No 结尾
- **实测**：10 服务 mvn compile 全过；响应侧缺口复扫 **0**、金额误注解 **0**
- **坑**：#63 读路径闭环收尾（写[0.2.58] rawBody + 读[0.2.82] 字符串化 = 双路径彻底闭环）；[0.2.81] 文档治理登记

## 2026-10-23 · [0.2.81] 文档治理（按商用版标准整理优化全仓文档）
- progress 42 段归档 + bug #1-#40 归档 + 5 报告迁 docs/reports/ + C 组立项合并 + 待办清单并入差距报告附录 B + ADR 并入技术方案 + docs/README 重写 + 14 处失效引用修复

## 2026-10-22 · [0.2.80] AI 深水区④ AI 评测回归集（离线确定性断言进 CI）
- **scripts/ai-eval/**：test_tool_schema（35 工具 schema 全量转换/名称唯一/参数完整）+ test_tool_routing（14 路由 × 正负样例，固化 #71/#76）+ test_injection_regression（#68 主集 + 4 类变体）
- **CI**：.github/workflows/ci.yml 新增 ai-eval job（python 3.12 + pip cache），失败即红
- **实测**：3 脚本全绿（schema 35 / routing 44 / injection 19）+ 全仓 pytest 70 passed；变异自证：注释 reminders → schema 必红 → 恢复全绿
- **首跑即抓 2 类真实缺陷**：#78 客服路由漏「会员有什么权益」口语；#79 注入防护漏「忘记你是助手」角色覆盖 + 英文注入——均已修并纳入回归

## 2026-10-22 · [0.2.79] AI 深水区③ 主动式智能提醒（从被动问答到主动服务）
- **引擎**：reminders.py 聚合三类提醒（降价=消费省钱助手 B 块现成通知 / 复购=订单品类周期 / 换季=衣橱季节覆盖）+ data/reminders.json 幂等去重 + ACTIVE→READ 状态机 + 每小时整点调度（只扫活跃用户，零推送）
- **端点/工具**：/reminders/mine + /scan + /read/{id}；reminders 工具（TOOLS 34→35）；路由命中「有什么提醒/该补货/换季」
- **前端**：BFF 透传模块 + H5 我的页 AI 提醒中心（未读 hint + 面板）
- **测试**：test_reminders 5 项 + 全仓 70 无回归 + py_compile + vue-tsc 过
- **端到端**：/scan 真实生成降价提醒 1 条 + mark_read；SSE「有什么提醒吗」→ tool_call reminders
- **坑**：#76 路由优先级（降价提醒被商家定价截胡 → price_alert 提前，回归固化）；#77 脚本编辑双坑（PowerShell \n 字面量 + .NET 相对路径）

## 2026-10-22 · [0.2.78] AI 深水区② AI 链路可观测（/metrics + 全链路埋点）
- **指标**：`app/metrics.py` 进程内计数器（工具调用/失败/耗时、LLM outcome 六类、SSE 连接/事件、chat、token、uptime）+ `GET /metrics` Prometheus 文本格式
- **埋点**：chat_service LLM 异常六类映射 + 工具耗时成败；api SSE 包装器连接/事件计数
- **测试**：test_metrics 7 项全绿 + 全仓 65 无回归 + py_compile 过
- **SSE 端到端（真实 LLM）**：初始渲染 → 转人工对话 95 事件 → chat 0→1 / sse 0→95 / llm 0→2 / customer_service 1 —— 单调递增 + Prometheus 可解析
- **坑**：#75 进程内单例 vs 多 worker（推模式取舍）；Grafana AI 面板遗留
- 遗留记录：监控编排加 Prometheus job 抓 8095（可选）；Grafana AI 面板

## 2026-10-22 · [0.2.77] AI 深水区① AI 在线客服（FAQ 知识库 + 转人工工单 + H5 工单卡片）
- **后端**：`customer_service.py` FAQ 7 类 + 工单持久化（ST 单号/状态机/分类）；`tools.py` 注册（TOOLS 33→34）；`chat_service.py` 客服路由置顶 + `support` SSE 事件
- **前端**：H5 对话页客服工单卡片（Message.support + SSE 分支）；`vue-tsc --build` 通过
- **测试**：13 项确定性测试全绿 + 全仓 57 无回归
- **SSE 端到端（真实 LLM）**：转人工 → 工单 ST295D81E94DC1 → support 事件 ✅；FAQ 运费命中 ✅（无建单）
- **坑**：#71 路由优先级（转人工被物流正则截胡）/ #72 ainvoke / #73 schema 结构 / #74 AI 端口谜题
- 工具数 **33→34**；清单「C 组商家侧客服二期」留痕

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


## 历史归档

> 早期进度段（共 42 段，早于 [0.2.70]）已归档至 `docs/archive/progress-history.md`，需要回溯时按版本号查阅。
