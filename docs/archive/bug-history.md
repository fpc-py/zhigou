# 智购 Bug 历史归档（docs/测试问题bug记录.md 早期记录）

> 由测试问题bug记录.md 归档而来，保留 #1–#40 全部记录；近期 bug 见 docs/测试问题bug记录.md。


> **路径导航（2026-10-23 文档治理）**：本文为 `docs/测试问题bug记录.md` 早期记录（#1–#40）的只读归档。

## #40 压测器把 HTTP 201 误判为失败 → "19% 错误率"假结论（工作流3 #9，2026-10-18）
- **现象**：分层压测首版输出"BFF 错误率 19.46%"，几乎导出"BFF 有 19% 故障"的错误结论；分路径采样后真相是写路径 100% 成功（201）。
- **根因**：BFF 写路径返回 **201 Created**（Nest 默认 POST 状态码），压测器断言只认 `st == 200`，20% 的写请求全部被误判为错误 → 19.46% ≈ 写路径占比。
- **修复**：成功码集改为 `{200, 201}`；并用分路径采样复核（纯写 201×1800、纯读 200×2400、混合 200×1772+201×415 全部零错误）。
- **教训**：压测断言必须覆盖接口真实成功语义（POST 201/202 等），"只看 200"会产出看似合理实则错误的容量结论；任何异常错误率必须先复现定位再写结论，不能直接信数字。

## #39 @CircuitBreaker 注解对同类 private 方法失效（工作流3 #7，2026-10-18）
- **现象**：Resilience4j @CircuitBreaker 注解加在 fetchSkuInfo（private 方法）上，熔断完全不生效。
- **根因**：@CircuitBreaker 走 Spring AOP 代理增强，只有**经过代理的外部调用**才生效；同类内部 self-invocation 绕过代理，注解不拦截（经典 AOP 坑，@Transactional 同理）。
- **修复**：改编程式——注入 CircuitBreakerRegistry，调用点 `registry.circuitBreaker(name).executeSupplier(call)` 包住远程调用，失败由既有 catch 降级；封装为公共库 ResilientHttp 组件，4 处调用点统一接入。
- **教训**：注解式降级/熔断/事务对"同类私有方法内部调用"一律无效；要注解生效需提 public + 自注入代理，侵入大，编程式更可控。

## #38 公共库 optional 依赖不传递导致 NoClassDefFoundError（工作流3 #10，2026-10-18）
- **现象**：公共库 TraceAutoConfiguration 注册 Feign 拦截器（实现 feign.RequestInterceptor），product-service 启动失败 `java.lang.NoClassDefFoundError: feign/RequestInterceptor`，Spring 上下文初始化中断。
- **根因**：公共库 openfeign 依赖声明 `<optional>true</optional>`（为避免强制下游携带），optional 依赖**不传递**给下游；下游无 Feign 类 → 自动配置类含 feign 方法签名 → 类加载即失败。
- **修复**：拆 TraceFeignAutoConfiguration（独立类 + `@ConditionalOnClass(EnableFeignClients.class)`），仅启用 OpenFeign 的服务装配 Feign 拦截器；TraceAutoConfiguration 只保留无 Feign 引用的 TraceIdFilter。
- **教训**：公共库可选依赖 + 自动配置 = 必须按条件拆类，任何方法签名引用可选依赖类都会炸。
## #37 SSE 断链重连 UI 缺口（工作流2，2026-10-18 发现 · [0.2.40] A3 修复 2026-10-19 复验通过）
- **修复（[0.2.40] A3）**：chat.ts 第 5 参升级为 SseOptions 透传 postSse；chat/index.vue 接 onReconnect 显示「小智连接中断，正在重连…（第 N 次）」状态条（reconnecting），onMessage('error') 含「连接已断开」→ failed 状态 +「重试」按钮（retrySend 复用 lastSend 重发不重复 push 用户消息）；done 复位 idle；sending 期间断链不静默置回可输入。
- **复验（确定性测试 ALL_PASS）**：本地 SSE mock 服务器两场景 —— ① 流中断后恢复：onReconnect(1) 触发 + 重连拿到第 2 个 token + done（PASS）；② 持续中断 maxReconnects=2：onReconnect(1,2) + 耗尽 error「连接已断开，多次重连失败，请稍后重试」+ done（PASS）。UI 状态机由这两个回调驱动，接线经 vue-tsc type-check 通过。
- **浏览器实测补充**：AI 8095 宕机时发消息 → BFF 502 → 前端显式提示「AI助手暂时不可用」（不再静默）；输入框 sending 期间锁定。注：AI 宕机走 HTTP 错误路径（确定性错误不重连），流中断才走重连状态条——两条路径均已用户可见。

- **现象**：浏览器对话页实测杀 AI 8095 → SSE 流中断，页面仅输入框恢复可用，**无任何「重连中/连接已断开」提示**；重启 AI 后新消息链路恢复正常（完整 token 流）。
- **根因**：sse-polyfill.ts 已实现 onReconnect（每次重连前回调）+ 重连耗尽发 `onMessage("error", ...)`，但 chat/index.vue **未接这两个回调**，断链/重连状态未映射到 UI。
- **影响**：用户视角表现为「回复突然停住且无反馈」，等待 15s 重连窗口无感知；可商用性受影响（用户会困惑/重发）。
- **修复方向**：chat.vue 接 onReconnect 显示「连接恢复中…（第 N 次）」、onMessage("error") 显示「连接已断开，多次重连失败，请稍后重试」+ 提供重试按钮；sending 期间断链不应静默置回可输入。
- **实测数据**：断链后页面 4 次重连（1s/2s/4s/8s 退避）期间 AI 未起 → 重连耗尽（无 UI 提示）；重启 AI 8095 后重发消息 → 完整 token 流正常。
## #36 · [0.2.33] 工作流2 AI 工程化 5 坑：ivfflat 小数据退化 / 护栏漏词 / mock 500 无 body / SSE 登录时序 / PowerShell 文件编辑（已修复 / 部分留痕）

- **坑1（真实性能 Bug，已修复）ivfflat 小数据退化**：pgvector `products_vec` 仅 5 条向量，索引 `ivfflat(lists=100)`。
  `EXPLAIN ANALYZE` 证实走 Index Scan、probe=1 只扫 1 个 list → 4 个真实查询 3 空 1 错，**Top-1 命中率 0%**。
  换 `HNSW(m=16, ef_construction=64)` 后 Top-1/Top-3 命中率 **100%**（0.7905/0.7095/0.7025/0.785）；
  另设 `min_score=0.50` 相关性阈值（无关查询最高 0.3751 被滤，命中下限 0.7025 保留，margin≈0.35）。
  **教训**：ivfflat 的 lists 数必须远小于数据量（行/lists≈1000+），小数据上列表近全空召回必然退化；索引选型要与数据规模匹配。
- **坑2（真实安全缺陷，已修复）护栏关键词漏「未搜索到」**：`_guard_result` 原 8 词不含「未搜索到」，
  而 search_products 空结果文案恰是「未搜索到相关商品。」→ 该路径不触发防编造护栏 → LLM 可对空结果自由编造。
  补全 `_NOT_FOUND_MARKERS` 15 词；回归测试 18 项全 PASS 固化。**教训**：护栏关键词表必须覆盖工具实际输出文案，回归测试是找缺陷的。
- **坑3（测试技巧，已固化）mock HTTP server 500 必须带 body**：无 body 的 500 让 httpx 抛 `RemoteProtocolError`
  （解析层错误）而非可重试的 `HTTPStatusError` → 故障注入测试把「该重试」误判成「解析失败」，掩盖真实行为。
  keep-alive 下 POST 未读 body 还会 `ReadError` → mock 用 HTTP/1.0 + do_POST 显式读 Content-Length。
- **坑4（环境技巧，留痕）SSE 全链路登录时序**：登录 token 在 `data.accessToken`（不是 data.token）；
  验证码**单次消费**（登录成功 redis 键即失效）；`docker exec 2>&1` 在 PowerShell 下污染 stdout（NativeCommandError 包装），
  取验证码按行正则 `^\d{6}# 测试问题 & Bug 记录

 提取；验证码过期 4001 是 GBK 乱码。
- **坑5（文件编辑，留痕）Edit 工具对刚读文件仍报「未读」**：本项目改走 PowerShell 文件级替换
  `[IO.File]::ReadAllText/Replace/WriteAllText`（显式 `New-Object Text.UTF8Encoding $false` 无 BOM），写回后回读校验；
  PowerShell 单引号字符串内反引号 `r`n 不执行转义（写坏过 import asyncio 行，用 `[char]96` 拼字面修复）；
  Linux 沙箱环境（无 Windows 路径）下改走 sed 精确替换 + python ast 语法验证。
- **验证**：test_tool_reliability.py 7 项全 PASS（flaky500 重试 2 次/持续 500 耗尽/404 不重试/慢接口超时/retries=0/POST 不重试/护栏降级）；
  test_forge_guard.py 18 项全 PASS；AI 模块语法编译 + import 通过；RAG/SSE 实测脚本交付（需用户环境 pgvector+BFF+JWT 复跑）。
## #35 · [工作流1] 并发幂等兜底三次修复失败：REPEATABLE READ 快照读 → FOR UPDATE 死锁 → REQUIRES_NEW 连接池耗尽 → READ COMMITTED 正解（已修复，含实测数据）

- **现象**：30 并发同 requestId 下单（`scenario1_idempotency_race.py`）——数据正确（唯一索引 `uk_user_request` 兜住，仅 1 单落库），但 HTTP 1×200 + 29×500（DuplicateKeyException）。「先查后插」幂等在并发窗口下靠唯一索引兜底，兜底直接抛 500，重复点击变「系统繁忙」
- **根因（三层，逐轮实证）**：
  1. **快照读陷阱**：MySQL 全局/会话均为 REPEATABLE READ（已查证）。事务内第一次 SELECT 建立一致性快照，catch 内回查仍读旧快照——并发线程刚提交的订单不可见 → 返回 null → rethrow → 500。日志铁证：catch 打了「重复下单(唯一索引兜底)」、selectList 已执行，但回查结果 null
  2. **FOR UPDATE 死锁**（第 2 版尝试）：回查改 `SELECT ... FOR UPDATE` 当前读 → 200 升到 23，但 28/30 报 `DeadlockLoserDataAccessException`——INSERT 撞唯一索引时 InnoDB 令事务持有冲突记录 S 锁等待，catch 内 FOR UPDATE 需 S→X 锁升级，多事务互持 S 锁升级 X 构成经典死锁
  3. **REQUIRES_NEW 连接池耗尽**（第 3 版尝试）：catch 内开独立事务回查（新快照、无锁参与）→ 7/30 报 `CannotCreateTransactionException: Could not open JDBC Connection`——30 并发 ×(主事务+REQUIRES_NEW)=60 连接需求 > HikariCP 默认池 10
- **修复**：create 事务显式 `@Transactional(isolation = Isolation.READ_COMMITTED)`——每语句重建快照，catch 内普通 SELECT 即可读到最新已提交的并发订单；无锁、无额外连接、无死锁（`OrderServiceImpl.create`，含注释说明弃用方案）
- **验证（修复后实测）**：30 并发 → **30×200、0 错误、1 单落库、29 幂等返回、4.1s**；100 并发 → **100×200、0 错误、1 单落库、99 幂等返回、2.1s**；修复前基线 1×200+29×500、12.5s
- **教训**：幂等兜底不是「catch 一下就完」——必须理解事务隔离级别决定「读得到什么」：REPEATABLE READ 快照读看不到刚提交的并发行；FOR UPDATE 锁读会引入 S→X 升级死锁；REQUIRES_NEW 会让连接占用翻倍打爆连接池。需要「读最新已提交 + 不加锁」时，READ COMMITTED 是标准解。面试叙事完整版见 `docs/交易一致性实测报告.md` 第四节

## #34 · [0.2.31] 售后凭证闭环 500 误判 + PowerShell 中文 body GBK 编码坑 + 端口核对教训（已定位 / 测试技巧）

- **现象**：① 直连 `POST /aftersale/apply`（body 带中文 reason + images 数组）返回 500「系统繁忙」，一度误判为 orderClient 校验链路故障；② 随后对 8088 调 `/order/mine`、对 8085 调 `/aftersale/apply` 均 500，疑环境故障
- **根因**：① **PowerShell 中文 body GBK 编码坑**——ConvertTo-Json 后 curl.exe --data-binary  按本地代码页（GBK）发送字节，中文 UTF-8 JSON 被破坏 → 请求体解析异常 → 500；② 打错端口：order-service 配 **8085**、aftersale-service 配 **8090**、marketing 8088、logistics 8089，与记忆中的端口表不符
- **修复（测试技巧，非代码缺陷）**：① JSON body 写 UTF-8 无 BOM 文件后 --data-binary "@file"（ASCII/文件方式即通，售后单创建成功）；② 权威端口以各服务 application.yml server.port 为准：8080 auth / 8081 user / 8082 file / 8083 product / 8084 cart / 8085 order / 8086 inventory / 8087 payment / 8088 marketing / 8089 logistics / 8090 aftersale / 8091 community / 8092 life / 8093 closet / 8094 wallet / 8095 AI
- **验证**：售后凭证闭环 PASS（orderNo=2106312063613272064 PAID 单 → 售后单 ASC5C7AE75454C4696 images 落库凭证 URL）；file_meta bizType/width/height 落库核对正确
- **教训**：curl 中文 JSON 一律 UTF-8 文件 + --data-binary "@file"，禁止管道直传；服务端口以 yml 为准不凭记忆；5200 类业务错误先自证请求构造再查服务

## #33 · [0.2.30] pgvector 维度不匹配（vector(1536) vs 实际 1024）+ MQ 消费者 Windows 不可用 + AI 跑系统 Python（已修复 / 留痕）（vector(1536) vs 实际 1024）+ MQ 消费者 Windows 不可用 + AI 跑系统 Python（已修复 / 留痕）

- **现象**：① `products_vec` 表自建起恒 0 行——任何 upsert 失败；② AI 8095 由系统 Python312 占用（venv 新代码不生效）；③ `rocketmq-client-python` 安装成功但启动报「rocketmq-python does not support Windows」
- **根因**：① `rag/service.py` 建表 `embedding vector(1536)`，实际模型 text-embedding-v4 输出 **1024 维** → pgvector 维度不匹配必报错；② 历史教训 #25③ 再现（重启时未用 venv）；③ rocketmq-client-python 基于 RocketMQ C++ SDK，官方不支持 Windows 平台
- **修复**：① `EMBEDDING_DIM=1024` 常量 + 建表 SQL 参数化 + drop 旧表重建；② 停系统 Python 进程 → venv `-m uvicorn main:app --port 8095` 重启；③ 留痕——增量链路（PRODUCT_CHANGED → embedding → upsert）在 Windows 本地不可激活，生产 Linux/容器部署 AI 后自动生效；本地验证改用直调 `_handle_message_async` + 全量回填
- **验证**：全量回填 4 条真实商品入 pgvector（1024 维、spuId 幂等）；RAG 检索「跑步运动鞋 轻量透气」→ top1 轻量运动鞋 0.8127；直调模拟 PRODUCT_CHANGED 消息 → 新商品 9000000000000000999 入向量库
- **教训**：pgvector `vector(n)` 维度必须与 embedding 模型实际输出一致（换模型先验维度）；Windows 上 MQ 消费者属平台限制，验证链路要分「逻辑直调」与「生产形态」两层口径

## #32 · [0.2.29] JS Number 精度丢失：BFF favorite/ids 返回 300 而非 201（已修复）

- **现象**：`GET /user/favorite/ids`（BFF 3000 透传）返回 `[1000000000000000300, 1000000000000000100]`，而 8081 直连与 Node 原生 http 均返回 `[1000000000000000201, 1000000000000000101]`；前端详情页收藏态判断受影响
- **根因**：**JS Number 精度丢失**——19 位 Snowflake Long（1000000000000000201）超出 2^53 安全整数，`JSON.parse` 后 201→300、101→100。已排除：axios keepAlive/连接池、dist mock、路由冲突、多进程、重启竞态、localhost/127.0.0.1 差异、代理环境变量、拦截器（均验证为死路）；`node -e` 验证 `JSON.stringify(1000000000000000201)` = `"1000000000000000300"` 实锤
- **修复**：后端 `UserProfileService.favoriteIds` 改返回 `List<String>`（`String.valueOf(spuId)`，与 ProductTrackItem.spuId ToStringSerializer 口径一致）；`checkFavorite` 改 `contains(String.valueOf(spuId))` 字符串比较；BFF/H5 已按 `string[]` 透传（前端无需改）
- **教训**：19 位 ID 跨层必须字符串透传（CLAUDE.md 红线再次验证——收藏/浏览/画像底座同款）；BFF 层禁止 Number() 化；`\uFEFF` BOM 与 ANSI 写回均会破坏 Java 文件（Java 源码必须 UTF-8 无 BOM）
- **验证**：8081 直连 + BFF 均返回 `["1000000000000000201","1000000000000000101"]`；H5 详情页刷新「已收藏」态正确（favoriteIds 字符串比较）

## #26 · [0.2.22] order Flyway mock 版本陷阱 + orderIds 字符串反序列化 500 + BFF Number() 丢精度（已修复）

- **现象**：① 新增 V20261002__fulfillment_action 被 order 库已应用的 mock V20261090 覆盖（版本更小被跳过），fulfillment_action 表未建，/order/stats/pending-fulfillment 报 500；② 后端 `List<Number>` 拒收 JSON 字符串 orderId，POST /order/fulfillment/action 返回 500；③ BFF 将 19 位 Snowflake orderId 经 `Number()` 化后回传，精度丢失致后端「订单不存在」
- **根因**：① order 库 flyway_order_history 已应用 V20261090__mock_order，新增迁移版本必须更大（同 inventory）；② Jackson 对 `List<Number>` 遇字符串元素抛 MismatchedInputException；③ JS Number 安全整数上限 ~9e15 < 19 位 Snowflake
- **修复**：① 迁移改名 V20261091__fulfillment_action.sql + clean package（清 target 残留）；② Controller 改 `List<Object>` + `String.valueOf` 中转解析；③ BFF 直接透传字符串 orderIds（不 Number() 化），后端双兼容
- **教训**：Flyway 新迁移版本 > 已应用最大版本是铁律（inventory/order 已各踩一次）；19 位 ID 跨层必须字符串透传（CLAUDE.md 红线），BFF 服务层禁止 Number() 化后再回传

## #27 · [0.2.23] 物流延误预警 3 坑：无 swagger @Operation / 轨迹排序 NPE / 端点无类级前缀（已修复）

- **现象**：① logistics-service 编译报「找不到 io.swagger」；② `GET /logistics/delay-alerts` 返回 500；③ `GET /logistics/delay-alerts` 返回 `NoResourceFoundException: No static resource logistics/delay-alerts`
- **根因**：① logistics 无 swagger 依赖；② `evs.sort((a,b) -> b.getNodeTime().compareTo(a))` 遇沙箱/空轨迹 `nodeTime=null` NPE；③ `LogisticsController` 类级 `@RequestMapping(produces=...)` 无路径前缀，`@GetMapping("/delay-alerts")` 实际映射到根路径 `/delay-alerts`，而 SecurityConfig 只放行 `/logistics/**` → 未授权请求落到静态资源解析 404
- **修复**：① 移除 @Operation 注解；② 改为 for 循环取最大 `nodeTime`（null 跳过）；③ 新端点显式 `@GetMapping("/logistics/delay-alerts")`、`@PostMapping("/logistics/dispatch")`（对齐已放行的 `/logistics/**`）
- **教训**：无 swagger 依赖的服务新增端点禁用 @Operation；业务排序前先判空；controller 无类级路径前缀时，方法级路径必须写完整并核对 SecurityConfig 放行范围

## #28 · [0.2.24] 售后助手 3 坑：新 Mapper 未注册 / LocalDateTime.parse 分隔符 / aftersale 全量 JWT 拦截 AI 直连（已修复）

- **现象**：① aftersale-service 启动失败 `No qualifying bean of type WarrantyInfoMapper`；② `POST /aftersale/repair/appointment` 500；③ AI `after_sale_repair` action=warranty 返回「暂时不可用」
- **根因**：① AftersaleServiceApplication 无 @MapperScan，现有 Mapper 靠 `@Mapper` 注解注册，新建 Mapper 未加；② `LocalDateTime.parse("2026-10-12 10:00:00")`（replace('T',' ') 后）非 ISO 格式 → DateTimeParseException；③ aftersale SecurityConfig `anyRequest().authenticated()` 全量拦截，AI 内网直连无 Bearer → 403
- **修复**：① 两个新 Mapper 补 `@Mapper`；② 直接 `parse(substring(0,19))` 保留 ISO T；③ SecurityConfig permitAll 增 `/aftersale/warranty/**`、`/aftersale/repair/**`（BFF 用户侧仍走 Bearer）
- **教训**：无 @MapperScan 的服务新增 Mapper 必须带 @Mapper；LocalDateTime.parse 只认 ISO `T` 分隔；AI 直连的端点须在 SecurityConfig 显式放行（内网口径），否则工具异常分支静默兜底

## #29 · [0.2.25] 经营大脑 4 坑：Flyway 版本撞车 / selectCount 强转 / TOOLS 未注册 / system.md 缺商家角色（已修复）

- **现象**：① forecast 接口返回空（daily_sales 未建表）；② mvn 编译报 `java.lang.Long 无法转换为 int`；③ SSE 商家 query 走兜底且模型自称「消费者导购」拒经营场景；④ 注册后模型仍不调用工具
- **根因**：① product 库 flyway_product_history 已应用 **V20261091（mock product review）**，新迁移同名被 Flyway 校验为已应用而忽略 → 新迁移必须比已应用最大版本更大（教训再次验证）；② `selectCount` 返回 `Long` 直接 `(int)` 强转非法 → 简化演示库存映射；③ **TOOLS 是显式列表**，新 `@tool` 定义在列表之后且未加入列表 → 工具未注册（`_tool_schemas` 无该 schema，路由强制也找不到）；④ system.md 顶部「AI 导购助手 / 目标用户是消费者」单角色定义，模型拒绝商家经营场景
- **修复**：① 迁移改名 `V20261092__merchant_forecast.sql`；② 删除 selectCount 分支，按 SKU 尾部映射演示库存；③ TOOLS 列表移至 tools.py 文件末尾并加入 `sales_forecast`（26 个）；④ system.md 顶部改双角色描述 + 新增「商家经营角色」工具表段落，chat_service `_ROUTE_RULES` 增商家经营确定性路由（`我是商家|店主|销量预测|智能选品|备货|该备多少货...`）强制首轮触发
- **教训**：TOOLS 显式列表场景下新增工具 = 定义 + 入列表（列表放文件末尾可避免顺序问题）；system.md 角色描述必须覆盖全部服务场景，模型默认会固守首段角色；商家侧工具要有确定性路由，不能只靠模型 auto 决策

## #30 · [0.2.26] BFF 服务名拼写错误：marketingService → marketingSvc（含 timeout 引用残留，已修复）

- **现象**：`GET /merchant/marketing-plan` 返回 500「系统繁忙」；日志 `Cannot read properties of undefined (reading 'timeout')`，栈指向 merchant.service.js marketingPlan()
- **根因**：BFF `service.config.ts` 的键名是 **`marketingSvc`**（`{ url: 'http://localhost:8088', timeout: 2_000 }`），新增代码误写 `SERVICES.marketingService`（undefined）→ get URL 与 timeout 均取 undefined；首次修复只替换了 get URL 一处，**timeout(SERVICES.marketingService.timeout) 残留**仍取 undefined
- **修复**：全量替换 `SERVICES.marketingService` → `SERVICES.marketingSvc`（含 timeout 引用），重建 dist 重启 3000 后 PASS（plan=3）
- **教训**：BFF 引用服务配置必须先核对 `service.config.ts` 的键名（marketingSvc 而非 marketingService）；同一个错误标识符可能出现在多处（URL + timeout + 其他管道参数），替换要全量，不能只替换首个可见处

## #31 · [0.2.27] AI 工具 f-string 引号/转义坑 + BizException 包路径 + ReviewReply import（已修复）
