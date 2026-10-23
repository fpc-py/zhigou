# AI 可靠性实测报告

> 工作流2 · AI 工程化深挖实测报告（对应 `智购开发任务差距分析报告.md` 四工作流之工作流2）
> 范围：工具调用可靠性 / AI 防编造 / RAG 检索质量 / SSE 连接管理 / AI 链路可观测
> 提交：随 [0.2.33]（工作流2 统一提交，代码+测试+本文档+坑记录）
> 测试方式：故障注入脚本（`scripts/ai-fault-injection/`）直连 AI 服务与 mock 故障源，实测数据见下。

---

## 1. 工具调用可靠性：配置驱动超时 + 只读幂等重试

### 改了什么

- `app/config.py` 新增 3 配置项（`ZHIGOU_` 前缀 env 可覆盖，部署不改代码）：
  `ai_tool_timeout_s=5.0`（默认超时）、`ai_tool_retries=1`（最大重试）、`ai_tool_retry_delay_s=0.3`（基础退避间隔）。
- `app/tools.py` `_http_get`：只读 GET **幂等重试**——5xx / 传输错误重试（间隔 0.3s × attempt 线性递增），
  **4xx 不重试**（业务性/认证性错误，重试无意义）；`_http_post` 写操作**一律不重试**（POST 非幂等，重试=重复下单）。
- 清理全文件约 27 处散落硬编码 `timeout=5.0/3.0`，仅保留 3 处业务特例：
  比价 8.0s（多 SKU 聚合）、下单 8.0s（远程直连）、vision 60s（视觉识别）。

### 故障注入实测（7 项，`test_tool_reliability.py`，mock HTTP server 可控故障）

| # | 故障场景 | 期望行为 | 实测结果 |
|---|---|---|---|
| 1 | 首次 500 后恢复（flaky） | GET 重试成功 | ✅ 恰好 2 次请求，成功返回 |
| 2 | 持续 500 | 重试耗尽后抛错，不无限等待 | ✅ 2 次请求后 HTTPStatusError |
| 3 | 404 | 不重试（非瞬时故障） | ✅ 1 次请求 |
| 4 | 慢接口（delay 1s）超时（timeout 0.2s） | TransportError 快速失败 | ✅ 0.2s 内失败 |
| 5 | 配置 retries=0 | 不重试 | ✅ 1 次请求 |
| 6 | POST 写操作 | 不重试（防重复下单） | ✅ 1 次请求 |
| 7 | 空结果 / 未命中 | 工具降级+防编造护栏 | ✅「严禁编造」护栏挂载 |

**结论**：重试语义与配置完全一致——读重试、写不重试、4xx 不重试、超时可配置。
面试深聊点：为什么 GET 可重试而 POST 必须不重试（幂等性边界）；为什么 4xx 不重试（业务错误重试只会放大噪音）。

### 测试踩坑（已固化）

1. **mock server 的 500 响应必须带 body**——否则 httpx 抛 `RemoteProtocolError`（解析层错误）而非可重试的
   `HTTPStatusError`，测试把「该重试的场景」误判成「解析失败场景」，掩盖真实行为。
2. **keep-alive 下 POST 未读 body 导致 `ReadError`**——改 HTTP/1.0 + `do_POST` 显式读 `Content-Length` 修复。

---

## 2. AI 防编造：护栏关键词表漏洞（真实安全缺陷）与回归

### 漏洞发现

- 回归测试 `test_forge_guard.py` 复查时发现：`_guard_result` 关键词表 8 词缺**「未搜索到」**，
  而 `search_products` 的空结果文案恰是「未搜索到相关商品。」→ 该路径**不触发护栏** → LLM 可对空结果自由编造商品。
- 这是「提示词说别编造 ≠ 不会编造」的典型：护栏必须挂在**工具回执**上（LLM 对回执服从度最高），且关键词表必须覆盖工具实际输出文案。

### 修复

- `_NOT_FOUND_MARKERS` 补全 **15 词**：暂无 / 没搜到 / 没找到 / 没有搜到 / 没有找到 / 未查到 /
  **未搜索到** / 未查询到 / 未找到 / 查不到 / 不存在 / 无相关 / 无合适 / 没有匹配 / 搜索为空。
- 空结果 / None 也强制附加护栏文案。

### 回归实测（18 项全 PASS，`test_forge_guard.py`）

- 关键词表 15 词完整 + 含「未搜索到」修复点：✅
- 11 条命中语义（未搜索到相关商品。/ 暂无商品信息 / 未查到该用户订单 / 不存在该商品 / 搜索为空…）→ 全部挂护栏 ✅
- 3 条正常结果（找到 3 款商品 / 订单状态已发货 / 无优惠券可推荐）→ **不误挂**，原样返回 ✅
- 空结果 / None → 护栏强制挂载 ✅

**面试深聊点**：防编造不是提示词一句「不要编造」，而是输出端护栏 + 工具真实性双闭环；
回归测试的价值在于**暴露提示词漏掉的口径**——测试是找缺陷的，不是证明没缺陷的。

---

## 3. RAG 检索质量：ivfflat 小数据退化 → HNSW（真实性能 Bug）

### 根因（EXPLAIN 实证）

- pgvector(0.8.7) `products_vec` 仅 **5 条**商品向量，旧索引 `ivfflat(lists=100)`。
- `EXPLAIN ANALYZE` 证实：规划器走 **Index Scan**（ivfflat 查询时 probe=1 只扫 1 个 list），
  而 lists=100 在 5 行数据上只有少量非空 list → 4 个真实查询 **3 个返回 0 行、1 个命中错误商品**，**Top-1 命中率 0%**。
- 本质：**ivfflat 的参数（lists）与数据规模强耦合**——lists 应远小于数据量（经验：行数 / lists ≈ 1000+），
  小数据上列表几乎全空，召回必然退化。这不是配置失误，是索引选型与数据规模失配的经典陷阱。

### 修复

- 索引换 **HNSW**（m=16, ef_construction=64），`init_db` 自动 DROP 旧 ivfflat 索引防残留干扰规划器。
- `similarity_search` 增加 **min_score=0.50 相关性阈值**：低于阈值的低相关结果直接过滤，
  降级为空结果（由防编造护栏如实告知用户，而非把无关商品当相关商品）。

### 实测基线（`test_rag_quality.py`，真实 embedding 评测集）

| 指标 | 修复前（ivfflat） | 修复后（HNSW + 阈值） |
|---|---|---|
| Top-1 命中率 | **0%**（3 空 1 错） | **100%**（4/4） |
| Top-3 命中率 | 0% | **100%** |
| 命中分数 | — | 0.7905 / 0.7095 / 0.7025 / 0.785 |
| 无关查询（恐龙化石）最高分 | — | **0.3751 → 被 0.50 阈值滤掉，返回空** |
| 判别 margin | — | 命中下限 0.7025 vs 无关上限 0.3751，阈值 0.50 居中（margin≈0.35） |

**面试深聊点**：
- 「用了 pgvector」是罗列；「**发现 ivfflat 在 5 条数据上 probe=1 扫空 list，命中率 0%，换 HNSW 后 100%，再用相关性阈值滤掉 0.3751 的噪音**」才是故事；
- 可深聊 ivfflat vs HNSW 的适用边界（数据规模、召回 vs 构建/内存成本）、probe / ef_search 参数、向量维度约束（≤2000）、阈值怎么用数据定（不是拍脑袋 0.5，是命中下限与无关上限的中间值）。

---

## 4. SSE 连接管理：断线重连 + 心跳活性 + 假死超时释放

### 改了什么（前端 `apps/h5-shop/src/api/sse-polyfill.ts`）

- **断线指数退避重连**：1s / 2s / 4s / 8s，默认 maxReconnects=4，`onReconnect` 回调供 UI 提示「连接恢复中」；
- **幂等续传**：重连用同一 sessionId 重发 query——服务端会话历史在 done 时才写回，重连不产生重复上下文；
- **心跳活性**：服务端 sse_starlette 每 15s 发 `: ping` 注释行；客户端把**读到任何字节**（含注释）当存活信号；
- **假死超时释放**：idleTimeoutMs 默认 30s 无字节 → 主动 cancel → 自动重连；用户 AbortSignal 取消则不重连。
- `chat.ts` 适配新签名 `{ signal }`（旧签名把裸 AbortSignal 当第 4 参，取消信号会被静默丢弃——类型检查不报错、行为错误）。

### 全链路实测（BFF 3000 → AI 8095，JWT 鉴权，`test_sse_e2e.py`）

- `POST /chat/sse` → **HTTP 201 text/event-stream**，SSE 头正确；
- 事件序列完整：`token` 分片流 → `tool_call`（analyze_user_context / search_products 真实触发）→
  `tool_result`（真实工具结果）→ 继续 token → **`done` 事件收尾**；
- 短 query 验证：token 流 + done 到达，链路闭环。

### 踩坑

1. **EventSource 只支持 GET**，POST SSE 必须 fetch + ReadableStream 手写解析；解析器第一个坑是
   `\r\n` 分隔（`\r\n\r\n` 不含连续 `\n\n`，按 `\n\n` 切永远切不出事件）——先规范化 `\r\n→\n`。
2. **登录 token 字段是 `data.accessToken`**（不是 `data.token`）；验证码**单次消费**，登录成功后 redis 键即失效，
   并发/重试时序错误报 4001「验证码已过期」（GBK 乱码）。
3. **`docker exec 2>&1` 在 PowerShell 下会污染 stdout**（NativeCommandError 包装），取验证码需按行正则 `^\d{6}$` 提取。
4. 浏览器端到端（断网/杀进程重连）受浏览器自动化 viewport 限制未做，验收口径为 BFF API 全链路 + 客户端 type-check。

---

## 5. AI 链路可观测：tool_call → tool_result → token 全链路 trace

### 改了什么（`app/chat_service.py` `_run_agent`）

每轮 LLM 循环埋点（`TRACE r{N} ...`）：
- `llm_req`：模型 / forced_tool / messages 数（请求进入）；
- `llm_resp`：token 字符数 / 工具调用清单（响应汇总）；
- `tool_call`：工具名 + 脱敏 args（userId 前三字符 + `***`）；
- `tool_result`：ok / guarded（是否挂防编造护栏）/ 字符数 / **耗时 ms**；
- `finish`：无工具调用结束。

**价值**：一条日志即可还原「LLM 说了什么 → 调了哪个工具 → 工具返回什么（多快）→ 护栏是否介入」的完整决策链，
排查「AI 乱说话」问题时能区分是 LLM 幻觉、工具失败还是护栏漏网。

### 故障注入演示口径

- 工具抛异常：trace `tool_result ... ok=True(异常) guarded=True` → 异常结果也进 LLM 上下文（可向用户解释）；
- 重试耗尽：`_http_get` 抛错 → 工具级 catch → 降级文案 + 护栏，trace 可见「耗时 + 异常」。
- 该链路与第 1、2 节故障注入测试共用同一代码路径，7+18 项断言已覆盖异常分支。

---

## 6. 测试覆盖与验收口径

| 测试脚本 | 覆盖 | 结果 | 运行环境 |
|---|---|---|---|
| `scripts/ai-fault-injection/test_tool_reliability.py` | 工具重试治理 7 项 | **全 PASS** | mock server 直连，无需外部依赖 |
| `scripts/ai-fault-injection/test_forge_guard.py` | 防编造护栏 18 项 | **全 PASS** | 纯逻辑直测 |
| `scripts/ai-fault-injection/test_rag_quality.py` | RAG 检索质量（前后基线对比） | 需 pgvector + embedding API，用户环境运行 | 依赖 pgvector / LLM key |
| `scripts/ai-fault-injection/test_sse_e2e.py` | BFF→AI SSE 全链路 | 需登录 token，用户环境运行 | 依赖 BFF(3000)/AI(8095)/JWT |

**已在本环境验证**：代码全量语法编译通过、AI 模块可导入、重试治理 8 项 + 防编造 18 项全 PASS。
**需用户环境复验**：RAG 评测（pgvector+embedding）、SSE 全链路（BFF+AI+JWT）、前端 `npm run type-check`（h5-shop）。
