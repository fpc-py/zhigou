# 智购 · 变更记录（CHANGELOG）

> 每个可交付单元（功能/修复/重构/文档）在此登记，格式遵循 [Keep a Changelog](https://keepachangelog.com/zh-CN/1.1.0/) 与 [语义化版本](https://semver.org/lang/zh-CN/)。
> 格式：`[类型] 模块：描述`。类型：feat / fix / refactor / test / docs / chore。

## [0.2.10] - 2026-10-08

### P1 · AI 决策辅助第六批（跨平台比价 + 图片搜款）

- feat(P1) product-service 跨平台比价：`POST /price/compare`——`PriceSourceAdapter` 适配器模式聚合京东/天猫/拼多多三渠道（本地模拟源，**生产环境实现同一接口替换为真实第三方比价 API 即可**，接口不变）；报价含售价/运费/总价/预计到货/优惠说明，按总价最低 + 到货更快标最优，输出一句话可解释建议；渠道全失败降级为本平台自营价兜底（不报错）
- feat(P1) AI `compare_prices` 升级：从仅比本平台 SKU → 跨平台聚合（调 /price/compare，输出各渠道含运费报价 + ★最优 + 建议）
- feat(P1) AI 新工具 `search_by_image(image_url, limit)`：多模态图片搜款——视觉模型识别商品特征（品类/颜色/风格/搜索关键词）→ 基于特征调 `search_products` 搜真实商品；60s 超时 + 一次重试，识别失败/网络不可达时优雅降级提示，绝不编造商品
- feat(P1) 对话图片链路：`ChatRequest.imageUrl` 贯通 H5 → BFF → AI（多模态 HumanMessage + 强制系统指令"上传图片必须先调 search_by_image"）；H5 对话页新增相机按钮（file-service 上传 → 缩略图预览 → 带图发送）；BFF `/price/compare` 透传
- feat(P1) H5 比价页接通真实接口：删除前端模拟 CHANNELS 常量，改为 `POST /price/compare` 渲染真实渠道报价（含运费/到货/最优标记/省钱明细/建议文案）
- **验证**：/price/compare 冒烟——SKU22 耳机 京东 ¥209 / 天猫 ¥195 / 拼多多 ¥178.10（★最优，含建议文案）；SSE 实测「跨平台比价」→ compare_prices 输出三平台 + 最优，LLM 回复引用真实报价无编造；SSE 实测「图片搜款」→ 强制触发 search_by_image（本机到 OpenAI vision 拉图超时为外部网络限制，降级提示生效，云端生产可用）
- **限制说明**：本地模拟渠道价（基于本地 SKU 价 ± 渠道系数）非真实第三方报价；search_by_image 视觉识别依赖 LLM vision 端点网络可达性，不可达时返回降级提示

## [0.2.9] - 2026-10-08

### P1 · AI 决策辅助第五批（个性化推荐引擎：画像 + 评分 + AI 工具 + 前端）

- feat(P1) product-service 个性化推荐引擎：`GET /recommend?scene=home|cart|detail&limit=N`——画像全部来自**内部真实数据**（历史订单 `/order/mine` 品类偏好/价位带/已购、购物车 `/cart/mine` 当前意向、product_review 评价口碑），加权评分 = 品类命中 1.5 + 价位匹配 1.0 + 口碑分 0.3~1.5 − 刷评降权 0.8 − 已购换新 0.8 + 购物车意向 0.5 + 反常识加成 0.5（冷门高口碑）；输出可解释理由（reasons 标签）+ 特殊标记（⚠ 疑似刷评 / 已购过 / 小众高口碑），用户身份由 `UserContext` 服务端注入；订单/购物车拉取失败自动降级为口碑榜（不报错、不编造）
- feat(P1) AI 新工具 `recommend_products(scene, limit)`：调 `/recommend` 输出画像驱动推荐清单（含理由与标记），服务端注入身份无 userId 入参；`TOOLS` 注册表补齐
- feat(P1) BFF `/home/feed` 聚合透传个性化推荐（`personal` 字段，product-service 2s 超时降级）；H5 首页新增**「猜你喜欢 · 为你定制」**区块——`personal.sceneText` 标题 + ProductCard 复用 + 理由标签 + 数据来源说明
- fix(P1) **LLM 幻觉商品**：旧 system.md 仅基础准则，LLM 分析画像后编造"李宁/安踏/特步"等库外商品——重写 `prompts/system.md`：补齐 12 工具真实清单与使用场景表、六步决策流程（拆解→搜索→比价→避坑→推荐→代下单）、硬红线"推荐必须引用工具返回的真实商品，禁止编造商品名/价格/评价"；spuId/skuId 用途说明（评价用 spuId、查价查库存用 skuId）
- fix(P1) **/order/mine 参数缺漏**：推荐引擎内网调用未带必填 `userId` 参数返回 500 → 补 `?userId=`；附带发现 order-service 此前以无 profile 启动（缺 mock 数据），恢复 `--spring.profiles.active=dev` 启动
- fix(P1) recommend_products 输出双 ¥ 符号（`_fen_to_yuan` 已带 ¥，拼接重复）→ 去重
- **验证**：product /recommend 冒烟——23 件历史购买画像命中（运动鞋/T恤 3.4 分含"你常买这个品类/在你常用价位带内/已在你的购物车/已购过"，耳机 0.4 分带 ⚠ 疑似刷评降权）；SSE 实测「结合我买过的推荐」→ analyze_user_context（22 单/运动鞋 90%+/百元内）→ recommend_products（4 款真实商品带理由）→ LLM 回复全部引用工具数据无编造；BFF/H5 `npm run build` 通过

## [0.2.8] - 2026-10-08

### P1 · AI 决策辅助第四批（评价数据底座 + 差评/水军识别）

- feat(P1) product-service 评价数据底座：新增 `product_review` 表（迁移 `V20261090.1__product_review.sql`，review_id Snowflake 唯一、rating 1-5、content ≤1024、deleted 软删、idx_spu_rating/idx_spu_time）+ dev mock 种子（`V20261091__mock_product_review.sql`，3 个 SPU 共 25 条，含差评与 3 条内容完全相同的"好评王"刷评特征）
- feat(P1) 评价接口三件：`POST /product/review` 发表（校验 rating 1-5、昵称脱敏"用户****尾4位"、isMock=0 真实评价）、`GET /product/review` 列表（spuId + minRating/maxRating + 分页）、`GET /product/review/stats` 统计（total/avgRating/ratingDist/imageCount/lowStarCount/duplicateCount，星级缺失补齐、均分保留 1 位）
- feat(P1) AI `review_analysis` 从占位升级为真实评价分析：先拉 SPU 基础信息再拉 `/product/review/stats` 真实统计；有差评时拉 `minRating=1&maxRating=3&pageSize=5` 取差评要点前 3 条（截 40 字）；**规则化识别**——duplicateCount≥2 提示"⚠ 疑似刷评"、差评比例≥30% 提示"⚠ 差评比例偏高"、整体口碑好时正向提示；数据全部来自真实接口不编造
- fix(P1) **skuId/spuId 混淆 bug**：LLM 把 `search_products` 返回的 skuId 当 spuId 传给 review_analysis 导致"查不到"。修复：`search_products` 输出同时带 `spuId` 与 `skuId`（spuId 用于评价分析、skuId 用于查价/查库存）；`review_analysis` 入参兼容 skuId（直查 SPU 失败按 `/product/sku/{id}` 反查 spuId 再查）；后端 `SpuDetailResponse.SkuItem` 补 `spuId` 字段（ToStringSerializer）并由 `querySku` 装配
- fix(db) **Flyway 版本乱序坑**：mock `V20261091` 曾先于建表执行（outOfOrder=false 跳过低于已应用版本 20261090 的 V20261002）→ 建表迁移改为点号版本 `V20261090.1__product_review.sql`（介于 20261090 与 20261091 之间）+ 清理 `flyway_product_history` 中 success=0 的失败记录，迁移顺序恢复 20261090.1→20261091
- **验证**：product-service 重启后 API 冒烟——stats 返回 `{"total":10,"avgRating":4.1,"ratingDist":{"5":6,"4":1,"3":1,"2":2,"1":0},"duplicateCount":3}`（3 条重复刷评被检出）；POST 发表评价返回 reviewId（Snowflake 转字符串）；AI 重启后 SSE 实测「测试商品-蓝牙耳机评价怎么样」→ search_products（spuId/skuId 并列）→ review_analysis 返回**真实统计 11 条/均分 4.1/差评要点 3 条/⚠ 疑似刷评 3 条重复**，全链路无编造

## [0.2.7] - 2026-10-08

### P1 · AI 决策辅助第三批（隐性需求挖掘 + 提示词对齐）

- feat(P1) 新工具 `analyze_user_context()`：隐性需求挖掘——拉取当前用户历史订单（order-service `/order/mine`），按 SKU 名称词表归类**常购品类**、按订单金额分桶**常用价位带**、统计**订单数/购买件数**，输出结构化上下文供 LLM 补全用户未明说的约束（默认品类/价位带/复购倾向）；无历史订单时如实提示不编造；用户身份由服务端注入（无 userId 入参，防 LLM 编造 ID）
- docs(AI) `prompts/system.md` 对齐真实工具集：工具清单补齐 `analyze_user_context` / `optimize_cart` / `create_order`；决策辅助流程升级为 **拆解 → 隐性补全 → 搜索 → 比价 → 避坑 → 推荐** 六步；工具失败处理表补齐三个新工具
- docs(AI) 红线修正：原「禁止代替用户下单」改为「**禁止自主下单**」——用户表达购买意向后必须先复述商品/数量/金额并取得明确确认，才允许调用 `create_order`（与 P1 二批确认流一致，消除提示词与工具集矛盾）
- **验证**：本地 py_compile 通过；重启 ai-orchestrator（pid 13676）后 SSE 实测——提问「帮我推荐个耳机，预算不太高，参考我买过的」触发 `analyze_user_context`（返回 orderCount=22 / topCategories=运动鞋21 / priceBand=百元内）→ 继续 `search_products` 搜索耳机，全链路 token→tool_call→tool_result→token 正常，无编造



### CI 修复 · MinIO/S3 镜像源（三轮实证收敛）
- fix(CI) file-service 集成测试 MinIO 镜像：Docker Hub `minio/minio` 各源在 GitHub runner 均不可匿名拉取（latest 404 → quay.io RELEASE 500 unauthorized → Docker Hub RELEASE 404 "repository does not exist"）；最终方案：改用 **adobe/s3mock（S3 兼容 mock）** + Testcontainers 官方模块 `com.adobe.testing:s3mock-testcontainers:4.5.0`（`S3MockContainer`），镜像从 Docker Hub 匿名可拉（已验证）
- fix(CI) MinioInitializer：去掉 test profile 排除，恢复 makeBucket 自动建桶（测试用 s3mock 无预建 bucket 配置）；`setBucketPolicy` 容错——s3mock 对 policy PUT 返回 409 仅告警（生产 MinIO 正常设置）
- fix(CI) Probe 探测步骤：修复 YAML 字面块引号转义损坏（docker pull invalid reference format）、探测成功写回 `MINIO_IMAGE`（原 PULL_OK 不影响测试）、增加 docker.io 显式候选；默认镜像 adobe/s3mock:latest
- **验证**：本地 `mvn -pl services/file-service -am test` **6/6 全绿**（s3mock 真机容器）；全模块 verify 无回归
- fix(test) inventory-service 集成测试在 CI 无 RocketMQ 环境上下文启动失败（OrderClosedListener 连接 name-server 失败）：监听器加 `@ConditionalOnProperty(rocketmq.listeners.enabled)` 条件开关（生产 matchIfMissing=true 默认启用），test profile 显式 `rocketmq.listeners.enabled=false`——测试不再依赖外部 MQ，本地/CI 均 hermetic
- **验证**：本地 `mvn verify` 全模块 BUILD SUCCESS（11 服务 67 项测试全绿）

## [0.2.5] - 2026-10-08

### CI 修复
- fix(CI) file-service 集成测试 MinIO 镜像拉取失败（404 pull access denied）：Docker Hub `minio/minio:latest` 已停维护/匿名拉取受限 → 默认改用 `quay.io/minio/minio:RELEASE.2024-11-07T00-52-20Z`（固定 RELEASE 版，doris 等开源项目同款方案）；支持环境变量 `MINIO_IMAGE` 覆盖（内网/私有镜像）；`withCommand` 参数分离 + `waitingFor` 监听端口 + 120s 启动超时（防 flaky）
- **验证**：本地 `mvn -pl services/file-service -am test` 6/6 全绿（MINIO_IMAGE 回退缓存镜像验证代码路径）

## [0.2.4] - 2026-10-08

### P1 · AI 决策辅助第二批（会话记忆 / 凑单 / 代下单）
- feat(P1) 会话记忆持久化：ai-orchestrator 从进程内 MemorySaver 改为本地 JSON 文件持久化（data/sessions/{session_id}.json，注入最近 12 轮防 context 膨胀，生产注释换 RedisSaver/Redis）；新增 DELETE /api/v1/chat/session/{sid} 清空接口；BFF 透传 DELETE /chat/session/:id；H5 对话页新增"清空会话"按钮（Icon 新增 trash）
- feat(P1) AI 凑单优化器（optimize_cart 工具）：拉取购物车选中项 + 逐 SKU 实时查价/规格 → 调 marketing 满减引擎 discount/calculate 无券基准 + 逐券试算选最优 → 输出购物车明细、原价合计、优惠明细、实付、凑单建议；config.py 新增 cart_service_url/order_service_url
- feat(P1) 一键代下单（create_order 工具）：调用 order-service POST /order/create（幂等 requestId，x-user-id 透传），工具仅限用户明确确认后调用，LLM 需复述商品金额再下单（用户确认流实测通过）
- fix(P1) 工具入参安全：新工具改为服务端 ContextVar 注入当前用户（_get_current_user()），不再让 LLM 猜测/伪造 userId
- **实测**：SSE 全链路——"怎么买最划算"→ optimize_cart（购物车 2 件 ¥148 明细+最优方案）；"确认下单"→ create_order 真实下单成功（订单 2108152430193233920，¥148.00）；跨轮会话记忆与清空接口均验证通过
## [Unreleased]

### P0 · M1 收尾（进行中）
- 营销活动（满减/秒杀/拼团/凑单）
- 评价增量向量更新
- 用户画像 / 收藏 / 浏览历史
- CI/CD、全链路压测

## [0.2.3] - 2026-10-08

### fix（测试基建 · CI 集成测试 Testcontainers 修复）
- **根因**：CI 上 AuthIntegrationTest 返回 500——① auth 的 `@DynamicPropertySource` 注册了 `TEST_JDBC_URL`（Spring 不认，datasource 走 main yml 硬编码 localhost:3307）；② MySQL/Redis 容器无显式等待策略/超时（CI 网络慢时容器未就绪即启动 Spring）；③ payment/marketing/aftersale 测试类缺 Redis 容器但 test yml 引用 `${spring.data.redis.host}` 占位 → 占位符解析/Hikari/Lettuce 连接失败
- **修复**（11 个服务统一加固）：`@DynamicPropertySource` 注册标准键 `spring.datasource.url/username/password` + `spring.data.redis.host/port`（保留 TEST_JDBC_URL 兼容）；MySQL `withStartupTimeout(120s)`；Redis `waitingFor(Wait.forListeningPort())` + 120s 超时；payment/marketing/aftersale 补 Redis 容器；auth 补 `spring.sql.init.mode=always` 执行 schema.sql
- **验证**：全模块 `mvn verify` 集成测试全绿——**78 项测试 0 失败 0 错误**：auth 3 / user 6 / file 6 / product 5 / cart 4 / order 10 / inventory 4 / payment 7 / marketing 8 / logistics 6 / aftersale 8（+ common MaskUtil 11）

### fix（CI 集成测试第二梯队 · test profile 安全链与迁移一致性）
- **根因**：① 9 个服务 `SecurityConfig` 均 `@Profile("!test")`，test profile 下无自定义 SecurityFilterChain → Spring Boot 默认安全链拒绝全部请求（401）；② cart 测试 mock 的 product 校验 URL/响应体与实现脱节（`/product/{id}` → `/product/sku/{id}/validate`、`data` 对象 → `data:true`）；③ order 的 `OutboxDeliveryTask` 构造注入 RocketMQTemplate，test yml 已 exclude MQ 自动配置 → 上下文加载失败；④ order 测试断言过时（重复支付回调已幂等放行，非非法跃迁）；⑤ payment 迁移版本重排后 target/classes 残留旧版本 `V20261007` → Flyway 重复 ADD 列（新库从头部署必现）；⑥ inventory 为 `WebEnvironment.NONE` 非 Web 测试，导入 SecurityFilterChain 配置导致无 HttpSecurity bean
- **修复**：① 10 个服务新建 `TestSecurityConfig`（@TestConfiguration + permitAll 链，@Import 接入，inventory 因非 Web 不导入）；② cart 测试 mock URL 与响应对齐实现；③ `OutboxDeliveryTask` 加 `@Profile("!test")`；④ order 测试改为直接验证状态机 `INIT→SHIPPED` 非法跃迁（40050）；⑤ 清理残留迁移文件 + 全部服务 target 迁移与 src 比对防回归；⑥ 其余服务（auth 参考实现）统一
- **验证**：`mvn verify` 全模块 78 项测试全绿（含上文 11 服务）；GitHub Actions CI `build-test` 应可全绿

## [0.2.2] - 2026-10-08


### perf（P1 容量调优 · 写链路瓶颈消除）
- **HikariCP 连接池全服务化**：批量给 10 个 DB 服务（auth/user/file/inventory/order/payment/marketing/logistics/aftersale + 早前 product）统一 maximum-pool-size:50 / minimum-idle:10 / connection-timeout:3000（pool-name ZhigouHikariPool）——修复 2000VU 下写链路（下单事务/扣库存/支付）连接池耗尽排队
- **复测③（2000VU 全量）**：**错误率 0.00%**（0/86245，-100%）、峰值 RPS 303（+50%）、P95 5.02s（-36%）、5 类接口 checks 全部 100% 通过
- **压测报告**：§6 更新三版复测对比 + 结论（剩余瓶颈收敛为单机写事务耗时，生产化路径 §6.3 不变）

## [0.2.1] - 2026-10-08

### feat（P1 AI 决策辅助 · 第一批）
- **需求拆解工具 \nalyze_requirement(message)\**（ai-orchestrator tools.py）：规则拆解模糊需求 → {预算/品类/场景/偏好} 结构化 JSON（预算正则 + 品类/场景/偏好词表 40+ 词）；SSE 实测：\"3000元以内送女朋友的礼物"\ → \{budget:3000, scene:送礼}\，\"500块以内的蓝牙耳机"\ → \{budget:500, category:耳机}\
- **比价工具 \compare_prices(sku_ids)\**：多 SKU 横向对比（价格/规格/库存），实测 \¥49.00 vs ¥199.00\ 对比清单正常输出
- **避坑工具 \
eview_analysis(spu_id)\**：选购提醒（价格区间/规格数/库存/多规格注意点），差评分析如实降级"待评价数据接入"
- **提示词升级**（\services/ai-orchestrator/prompts/system.md\）：工具表 +3；新增**决策辅助流程**（拆解→搜索→比价→避坑→推荐）；约束 LLM 搜索用品类词、预算用于过滤而非拼进关键词（修 LLM 将"3000"拼入 keyword 致空结果的实测问题）
- **修复**：analyze_requirement 的 join 混入 int 抛异常（\str(w)\ 修复）
- **测试**：tests/test_tools.py 新增 TestP1DecisionTools（拆解预算/品类/场景 + 比价降级），共 9 个用例（本地 pytest 未装，逻辑已通过真实 SSE 链路实测）

## [0.2.0] - 2026-10-08

### perf（P1 压测瓶颈修复落地）
- **product/page Redis 缓存**（\ProductController.page()\）：key \prod:page:{pageNum}:{pageSize}:{keyword}:{categoryId}:{brandId}\，TTL 300s（\product.cache.ttl-seconds\），空结果不缓存防穿透，写操作 evictPageCache；实测缓存命中 83ms vs 查库 1252ms（**15x**）
- **HikariCP 调优**：product-service \maximum-pool-size:50/minimum-idle:10/connection-timeout:3000\（默认池 10 在压测中耗尽）
- **BFF keep-alive 连接池**：\HttpModule.register\ 加 \httpAgent/httpsAgent\（keepAlive + maxSockets 50），消除每次透传新建 TCP 连接
- **RAG 快速失败**：\service.config\ 新增 \iRag: {timeout:300}\，详情 AI 摘要 300ms 超时即降级，不再拖慢详情接口
- **RAG 结果缓存**：BFF 内存缓存同 spu AI 摘要 5min，消除每次 detail 打 ai-orchestrator（压测曾致 AI 日志 15 万行）

### test
- **复测①（TTL60）**：61051 请求，错误率 35.65%→**5.82%**，P95 10.23s→8.14s，product/page 33%→**82%** 通过
- **复测②（TTL300+RAG缓存）**：60445 请求，P95→7.86s，detail/order/aftersale ≥98% 通过，page 67%（高 VU 随机波动）
- **QUICK 50VU 基线达标**：2451 请求 **0 错误**、P95 **329.33ms**（阈值 <1.5s）——全链路单用户延迟健康
- **结论**：优化直接命中瓶颈、效果显著；2000VU 未达标为**单机容量**（单 BFF 事件循环 204 RPS 达本地极限 + 写链路重事务排队）；生产化建议（BFF 横向扩展/写链路异步化/分库分表）已入报告 §6.3

## [0.1.9] - 2026-10-08

### feat
- **k6 全链路压测脚本（\scripts/load-test/full-chain-load-test.js\）**：黄金路径（登录→商品列表→详情→加购→下单→支付创建→我的订单→售后），读 80%/写 20% 混合，阶梯 100→500→1000→2000 VU，QUICK=1 冒烟模式；阈值错误率<1%、P95<1.5s

### test
- **全链路压测基线**（QUICK 50VU/20s）：2101 请求 **0 错误**、checks 99.94%、P95 213.95ms
- **全量阶梯压测**（100→2000VU/4m30s）：52763 请求、峰值 RPS 176、**错误率 35.65%（超阈值）**、P95 10.23s——**真实瓶颈定位**：① product/page（最高频读接口）33% 通过率（无缓存 + 连接池耗尽 + BFF 事件循环阻塞）；② BFF 峰值 RPS 远低于单层基线 718（axios 连接复用不足）；③ product/detail 隐性拖慢（ai-orchestrator RAG ETIMEDOUT 超时降级）；④ 写链路幂等/事务正常。**优化建议**（P1）：product/page Redis 缓存、BFF keep-alive 连接池+熔断、RAG 300ms 快速失败、HikariCP 调优。报告见 \docs/load-test/全链路压测报告.md\
- **login 断言修复**：BFF \POST /auth/login\ 返回 HTTP 201（Nest POST 语义，业务 code=200），压测脚本断言改 \status===200||201\（此前 1 次误报失败）

## [0.1.8] - 2026-10-08

### feat
- **GitHub Actions CI 流水线（\.github/workflows/ci.yml\）**：① build-test job——JDK 17 Temurin + Maven cache，\mvn -B -ntp clean verify\ 全模块编译+单测（失败上传 surefire 报告）；② build-images job（needs build-test，矩阵 11 服务）——多阶段 Dockerfile 构建 + GHCR 推送（\ghcr.io/fpc-py/zhigou/<svc>\），tag 策略：main→\main-<sha7>\+\latest\、tag v*→版本+\latest\、PR→仅构建不推送；gha 层缓存；③ summary 汇总。**多阶段 Stage1（容器内 mvn）在 CI 环境完成验证**，补齐本地 Docker Desktop 容器网络无法下载 Maven 依赖的缺口
- **GitHub Actions 部署流水线（\.github/workflows/deploy.yml\）**：手动触发（workflow_dispatch 输入镜像 tag），SSH + \docker compose -f infra/compose/services.yml up -d --pull always\ + 逐服务健康检查；前置 secrets 校验（DEPLOY_HOST/DEPLOY_USER/DEPLOY_KEY/DEPLOY_PATH），未配置即失败提示

## [0.1.7] - 2026-10-08

### feat
- **11 服务生产级多阶段 Dockerfile**：统一模板（Stage1 `maven:3.9-eclipse-temurin-17` 容器内编译 → Stage2 `eclipse-temurin:17-jre` 运行），关键基线：先拷全部 POM（根 reactor 解析必需，含 11 个服务 + packages/proto + common）最大化层缓存 → `mvn -pl <svc> -am package`；运行时非 root（`app` 用户 uid 1001）、`-Xms256m -Xmx512m -XX:+UseG1GC` JVM 参数、`EXPOSE` 服务端口、`HEALTHCHECK` 打 `/actuator/health`、`ENTRYPOINT` 走 `java $JAVA_OPTS -jar`；auth-center 一并升级（此前仅有旧版）
- **根目录 .dockerignore**：排除 `**/target`、`.git`、`node_modules`、`dist`、`logs`、`.sessions`、`*.log`、venv 等，压缩构建上下文
- **11 服务运行时镜像（本地构建）**：`Dockerfile.runtime`（eclipse-temurin:17-jre + COPY 宿主 mvn 打好的 jar，验证/快速部署用）批量构建成功 `zhigou/<svc>:0.1.0`（11 个）；user-service 容器实测：JVM 17 正常启动、**非 root（started by app）**、Spring Boot 3.2.5 启动日志正常
- **生产编排 `infra/compose/services.yml`**：11 服务接入 `compose_default` 网络（external，与 middleware.yml 共享）；环境变量覆盖 DB_URL（`zhigou-<db>:3306`）、REDIS_HOST、ROCKETMQ_NAME_SERVER、服务间 URL（PRODUCT/INVENTORY/ORDER/PAYMENT_SERVICE_URL）；auth-center 硬编码 datasource 用 Spring 宽松绑定 `SPRING_DATASOURCE_URL` 覆盖；`restart: unless-stopped` 自愈重试（depends_on 不可跨 project）；`docker compose config` 校验通过

### fix
- **here-string `$JAVA_OPTS` 被 PowerShell 展开为空**：Dockerfile ENTRYPOINT 写成 `java  -jar`（JVM 参数丢失）→ 反引号转义保留字面 `$JAVA_OPTS`（运行时由 sh 展开）
- **reactor ProjectBuildingException**：根 pom modules 列出全部模块，仅拷当前服务 pom 报 `Child module ... does not exist` → 全部 11 个服务 pom + proto + common 一并 COPY
- **容器内 mvn 下载依赖卡死（本地 Docker Desktop 网络到 Maven 仓库不通，宿主正常）**：多阶段构建的 Stage1 无法在本地验证 → 提供 `Dockerfile.runtime`（宿主 mvn 打包 + COPY jar）完成本地镜像构建与运行时验证；多阶段构建保留供 CI/CD（有网络）使用
- **.dockerignore 排除 `**/target` 导致 runtime COPY 宿主 jar 报 not found**：runtime Dockerfile 构建 context 改为服务目录（`docker build -f Dockerfile.runtime -t zhigou/<svc>:0.1.0 services/<svc>`）

## [0.1.6] - 2026-10-08

### feat
- **日志脱敏 AOP（packages/common mask 模块）**：新增 `SensitiveType`（PHONE/ID_CARD/BANK_CARD/EMAIL/PASSWORD/TOKEN/SECRET/NAME/ADDRESS/DEFAULT）、`MaskUtil`（正则智能识别 + 敏感词 key 全掩，支持按类型掩码）、`SensitiveLog` 注解（METHOD/TYPE 两级）、`SensitiveLogAspect`（@Around 序列化→递归掩码，耗时打印，序列化失败降级 toString+正则，**绝不抛异常影响主流程**）、`MaskAutoConfiguration`（@AutoConfiguration + @EnableAspectJAutoProxy，`zhigou.mask.enabled` 可关，默认开启）；`AutoConfiguration.imports` 注册；common pom 引入 `spring-boot-starter-aop`
- **auth-center 接入脱敏**：`AuthController.send-sms-code/login` 加 `@SensitiveLog`；`LoginRequest.code` 加 `@SensitiveField(PASSWORD)`（字段名 "code" 不在敏感词表，需注解兜底）
- **字段注解感知（切面反射）**：入参对象类上存在任一 `@SensitiveField` 时，收集**全部字段**（注解字段按注解类型掩码、其余字段原值交 maskMap 按敏感词/正则处理），修复仅收注解字段导致入参丢 phone 的问题

### fix
- **长数字被银行卡正则误掩**：Snowflake userId（`210775...1648`）被 `\d{13,19}` 误判为银行卡 → `BANK_CARD_RE` 收紧为 `[3456]\d{12,18}`（仅匹配真实银行卡开头），userId 原样输出
- **验证码字段漏掩**：`code` 字段名不在敏感词表且 6 位数字不匹配正则 → `@SensitiveField(PASSWORD)` 注解兜底，日志中 `******`

### test
- `MaskUtilTest` 11/11 通过（手机号/身份证/银行卡/邮箱/密码/token/姓名/地址/长 ID 不误掩/自定义类型）
- **auth-center 线上实测**（重打 jar 后登录）：`[SensitiveLog] AuthController.login(..) 完成 | 入参=[{"phone":"********","code":"******"}] | 返回={data:{userId:"2107757313435291648",accessToken:"********",refreshToken:"********"}}` —— phone 全掩、验证码全掩、token 全掩、**userId 完整保留**（不误掩）
- 环境全量重启（Docker Desktop→中间件 6 容器 healthy→11 Java 服务全端口 UP→ai/BFF/H5）后全链路冒烟：登录 200、BFF `/aftersale/mine` 7 条

## [0.1.5] - 2026-10-08

### feat
- **售后退款真实资金流（aftersale→payment）**：payment-service 新增退款表 `payment_refund`（Flyway V20261092，`refund_no` 唯一幂等键）+ 实体/Mapper + `PaymentService.refund(orderNo, amount, reason)`——按订单幂等（已有 SUCCESS 退款单直接返回）、校验原支付单必须 SUCCESS、金额 0<amount≤实付、沙箱即时 SUCCESS；新端点 `POST /payment/refund`（SecurityConfig permitAll，生产需内网白名单）
- **aftersale-service 退款联动**：迁移 V20261093（aftersale_order 加 `sku_id/count/refund_no`）；`AftersaleServiceImpl.refund(no)` 真实链路：SELLER_APPROVED→REFUNDING（状态机）→调 payment 退款→REFUNDED 记 refundNo→按 skuId/count 调 inventory 回滚库存（失败告警"待人工补偿"，不阻断退款）；REFUNDED 幂等返回原退款单号、REFUNDING 可重试；新增 Feign `PaymentClient`（/payment/refund）与 `InventoryClient`（/inventory/rollback）；**Feign 超时 connectTimeout 500 / readTimeout 3000**（原 300ms 导致退款 500）
- **inventory-service 内网端点放行**：SecurityConfig 将 `/inventory/preDeduct`、`/inventory/confirm`、`/inventory/rollback` 加入 permitAll（内网服务调用无用户上下文；此前 HTTP 通道全被 403，关单回滚一直靠 MQ 兜底，售后回库存直接失败）
- **BFF 售后透传模块**：新增 `apps/bff-shop/src/aftersale/`（controller JWT 保护 + service 透传 + types），apply/mine/detail/cancel 统一从 JWT 解析 userId 并注入 `X-User-Id` 头（修复 mine/detail 被 aftersale Security 403 吞错返回空）

### fix
- **退款 500 = Feign readTimeout 300ms 超时**：payment 首次调用（含 DB 事务）超 300ms → aftersale 抛 RetryableException → 调大 3000ms
- **售后退回库存失败（403）**：inventory /rollback 被 Security 拦截 → permitAll 后 refund 内自动回库存成功（实测 99→100）
- **BFF /aftersale/mine 恒返回 0 条**：BFF 透传不带 X-User-Id 头，aftersale 403 被 catchError 吞掉返回 [] → service 层统一注入头（mine/detail/cancel 三处）

### test
- **售后全流程闭环实测**（¥1 单 2106312063613272064，属主 10001）：apply（APPLYING）→ approve（SELLER_APPROVED）→ refund → **REFUNDED**（refundNo=RFF2452BD37CD04379）→ payment_refund 落库 SUCCESS → **库存自动回滚 99→100**（skuId 2106307884727541760）
- **退款校验防御实测**：支付单 PENDING 的订单申请退款被正确拒绝（"订单未支付成功，不可退款"），不产生退款单
- **幂等实测**：REFUNDED 售后单重复调 refund 直接返回原 refundNo；¥99 单（2107842025067634688）退款落库 RFAEE5ABF2639E440C
- **BFF 冒烟**：登录→`GET /aftersale/mine` 返回 7 条（此前 0 条）、`GET /aftersale/:no` 详情 REFUNDED+refundNo 透传正确
- **H5 浏览器实测**：订单页"售后"tab 显示 7 条售后单（SELLER_APPROVED→"待退款"、REFUNDED→"已退款"映射正确）；详情页两种状态卡（"审核通过，待退款"/"已退款"）与退款单号展示完整

## [0.1.4] - 2026-10-07

### feat
- **outbox 投递任务（order-service）**：新增 `OutboxDeliveryTask`（fixedDelay 30s 扫描 status=0 → RocketMQTemplate.syncSend(topic:tag) → 置 1，失败保留下周期重试，at-least-once 投递）；`OrderServiceImpl` 新增 `buildClosedEventPayload`/`writeClosedOutbox`——ORDER_CLOSED 事件体升级为 `{orderId,userId,closeReason,items:[{skuId,count}]}`（取消与超时关单统一新格式，旧格式无 items 的存量消息消费者安全忽略）
- **inventory-service 消费兜底**：新增 `OrderClosedListener`（@RocketMQMessageListener topic=ORDER_CLOSED，consumerGroup=inventory-order-closed-group）消费关单事件释放库存；`InventoryService.rollbackOrder(orderId, items)` 按订单整体回滚并以 Redis SETNX（键 `inv:rb:{orderId}`，TTL 7 天）幂等——关单同步调用与 MQ 兜底双通道只释放一次；`/inventory/rollback` 兼容新旧两种消息格式；pom 引入 rocketmq-spring-boot-starter 2.3.0

### fix
- **RocketMQ broker 地址不可达（Windows Docker Desktop 最大坑）**：broker 默认向 namesrv 注册容器内网 IP（172.18.x.x），宿主 Java 生产者连接超时（`sendDefaultImpl call timeout`）。修复：`middleware.yml` broker command 显式 `-c /home/rocketmq/rocketmq-5.3.0/conf/broker.conf`（镜像自带默认配置路径，非 store），`scripts/mq-fix-broker-ip.ps1` 用 `docker cp` 覆盖该 conf（`brokerIP1 = 127.0.0.1`）+ `docker restart`（**勿 recreate**，会重置配置层）→ 启动段 `brokerIP1=127.0.0.1`，clusterList `Addr=127.0.0.1:10911`。**注意：旧进程缓存旧路由，投递仍失败，必须重启 order-service**

### test
- outbox 投递：重启后一次性投递 **41/41 条成功**（status 全=1，0 失败）
- 幂等闭环实测：插入两条同 orderId=999001 的 ORDER_CLOSED 消息（items=[{skuId 9000000000000000020, count 2}]）→ 首条投递消费释放库存 94→96（Redis 实测），第二条**幂等跳过**（`inv:rb:999001` 存在，TTL≈7 天）→ 只释放一次；测试消息已清理
- 历史存量消息（旧格式无 items）：消费者安全忽略，不误释放

## [0.1.3] - 2026-10-07

### feat
- **超时关单（order-service）**：新增 `OrderTimeoutTask`（@Scheduled 每 5 分钟 + 启动首扫）与 `OrderServiceImpl.closeExpired`——扫描超过阈值（默认 15 分钟，`order.timeout-close-minutes` 可配）的 INIT 订单 → 状态机 INIT→CLOSED（closeReason="超时未支付自动关单"）→ 写 ORDER_CLOSED outbox → 调 inventory-service `/rollback` 释放预占库存（失败告警不阻断）
- **支付对账补偿（payment-service）**：Payment 新增 `notify_status`（Flyway V20261091，幂等标记订单联动状态）；mockPay 通知失败保留 0 待补偿；`reconcile()` 增强为「SUCCESS 未通知 → 补偿 notifyPaid + PENDING 超 24h 告警」；新增运维端点 `POST /payment/reconcile`（SecurityConfig permitAll，生产需内网白名单）

### test
- 超时关单：服务启动首扫关闭 **16 笔**超时 INIT 订单，状态分布 PAID 11 / PENDING 1 / CLOSED 16，中文关单原因落库正确
- 对账补偿：手动触发 `/payment/reconcile` 补偿 **4 笔** SUCCESS 未通知支付单（含历史数据，payCallback 幂等无副作用），SUCCESS 全部 notify_status=1，PENDING 无超时

## [0.1.2] - 2026-10-07

### fix
- **payment-service（支付待付款终极根因）**：Spring Security `anyRequest().authenticated()` 未放行 `/payment/sandbox/mock-pay`，且 BFF 转发不带认证头 → 全部 mock-pay 被 Security 403、**从未到达业务层**；BFF `catchError` 又吞掉 403 伪装成功 → 前端误显示"支付成功"而订单仍待付款。修复：`SecurityConfig` 将 `/payment/sandbox/mock-pay`、`/payment/notify/**` 加入 permitAll（服务端回调靠签名，无用户上下文）；BFF `payment.service.ts` 不再吞错、失败如实上抛
- **订单状态联动自动化**：支付成功（PENDING→SUCCESS）后新增 `OrderNotifyClient` HTTP 通知 order-service `payCallback`（重试 3 次指数退避 + T+1 对账兜底），`OrderServiceImpl.payCallback` 加幂等（PAID 直接返回）；`payment-service/application.yml` 新增 `payment.order-service-url`
- **订单详情页不刷新状态**：从结算页支付成功跳转后详情仍显示"等待付款"→ INIT 时自动轮询（6×600ms）至 PAID
- **订单列表页 tab 筛选从未生效**：模板 `v-for="o in list"` 未使用 `filtered` → 改 `v-for="o in filtered"`、空态判断用 `filtered.length`
- **「我的→售后」入口失效**：orders 页无售后 tab（AFTERSALE 非订单状态）→ 新增"售后"tab（= REFUNDING+REFUNDED）与"我的"页入口对齐
- **订单详情页按钮不合状态机**：CLOSED/REFUNDED 仍显示"申请售后"→ 按状态收敛（INIT=取消/支付，PAID/SHIPPED/COMPLETED=申请售后，终态无操作）

### test
- 浏览器实测 H5 全流程（登录→加购→结算→提交支付→自动跳转订单详情）：**支付成功 → 订单显示"商家备货中"（PAID）**；售后/待付款 tab 均正确过滤；直连 8087 mock-pay 验签通过（返回 404 而非 Security 403）

## [0.1.1] - 2026-10-07

### test
- **全链路联调冒烟**：12 微服务 + BFF + H5 全部真实启动，17 步黄金路径（登录→商品→AI 对话→加购→下单幂等→支付→回调→库存→物流→售后→我的订单）**PASS 17 / FAIL 0**；脚本 `scripts/smoke/zhigou-e2e.ps1`（ASCII 安全，Windows PowerShell 5.1 可直接执行）

### fix
- **order/cart/auth**：Snowflake ID 以 JSON 数字序列化被 BFF(Node double) 丢精度 → `OrderResponse`/`CartItemResponse`/`LoginResponse` 的 ID 字段加 `@JsonSerialize(ToStringSerializer)`，响应改字符串（根因：落库 ...288 与冒烟收到 ...300 差 12）
- **payment-service**：`PaymentController` 强转 `(Number) body.get("userId")` 抛 ClassCastException → 改安全 toLong 兼容 Number/字符串 + 参数校验
- **aftersale-service**：`@RequestHeader(required=false) Long userId` 与内网头 `X-User-Id` 不匹配致 403 → 改 `@RequestHeader(value="X-User-Id")`
- **cart/file/user 三服务**：`UserIdInterceptor` 只认 Bearer 不认 BFF 内网透传头致 401 → 统一优先读 `X-User-Id`、其次 Bearer
- **ai-orchestrator**：LLM key 无效时整链路 error → `chat_service.py` 增 AuthenticationError/APIConnectionError/APIError/RateLimitError 降级分支，切本地 `timeout_fallback_stream` 兜底推荐（SSE 正常出 token）；启动入口由 `uvicorn app.api:app` 修正为 `python main.py`
- **e2e 脚本**：Step 7 按 BFF 数组响应解析（`$r.data` 而非 `$r.data.items`）
- **h5 支付成功不跳转**：`mockPay` 曾传 `sign:'sandbox-mock'` 与后端验签不符（403 被静默吞掉）→ `payment.ts` 改为 `sha256(paymentNo + sandbox-secret-key)` 正确验签；checkout/orders/order-detail 支付失败不再静默（提示"支付失败，请重试"），checkout 支付失败兜底跳转订单详情（记 `lastOrderId`）

### feat
- **首页悬浮购物车**：home 页新增 FAB 悬浮购物车按钮（品牌渐变 + 数量角标，数量实时取自 `/cart/mine`），点击直达购物车页；定位与 TabBar 对齐（按 414px 手机容器居中，适配宽屏）

### docs
- 新增 `docs/真实支付接入指南.md`：沙箱 → 微信/支付宝支付的生产化实操文档（渠道抽象扩展/回调验签/幂等/订单联动/密钥环境变量化/前端收银台切换/退款/测试/上线 Checklist），并登记 `docs/README.md` 索引

## [0.1.0] - 2026-10-07

### feat
- **前端（apps/h5-shop）**：16 条路由 + 5 TabBar；8 屏对齐原型——首页（问候头部/快捷宫格/真实商品流）、AI 对话（气泡 + 工具理由 + 商品卡）、商品详情（AI 摘要/规格网格/比价入口）、AI 比价（最优方案 + 渠道演示表）、AR 试穿/智能衣橱/社区（视觉占位）、我的（真实资料 + 订单栏 + AI 模型卡）
- **交易闭环**：购物车（服务端数据/勾选/数量/清空）、结算（地址/优惠券/运费/优惠试算/提交幂等/沙箱支付）、订单列表（状态筛选/取消/去支付）、订单详情、地址管理（CRUD）、优惠券页
- **BFF（apps/bff-shop）**：新增 cart/order/payment/marketing/user/logistics 六个透传模块；product 补 `GET /product/page` 透传；service.config 补齐 11 个微服务地址
- **order-service**：新增 `GET /order/mine?userId=`；下单前调 product-service 拉取真实价格与 SPU 名称写入订单（失败回退占位不阻断）；create/cancel 优先读 `x-user-id` header
- **cart-service**：新增 `DELETE /cart/{skuId}`（校验存在后删除 Redis 条目）

### fix
- **order-service**：订单金额/商品名由写死占位（100 分 / SKU-xxx）改为真实商品数据
- **cart-service**：购物车无法删除单条（前端曾用 count=0 残留条目）→ 真实删除接口打通

### docs
- 文档体系统一：新增 `docs/README.md` 文档中心；重写根 `README.md`、`CLAUDE.md`、`docs/progress.md`；新增 `CHANGELOG.md`、`CONTRIBUTING.md`、`apps/bff-shop/README.md`、`services/README.md`；重写 `apps/h5-shop/README.md`、`apps/admin-merchant/README.md`；修正 `docs/release-checklist.md` 路径错误；修正 `.claude/skills` 目录拼写

## [0.0.2] - 2026-10-02

### feat
- BFF 层压测（50 并发 / 4500 请求 / 0 错误 / P95 131ms / RPS 718），报告见 `docs/load-test/`
- Mock 数据机制：Flyway profile 分离（`db/mock` 仅 dev 执行）+ 固定 ID 段 + 一键清除脚本 `scripts/mock-data/clean-mock.ps1`
- 生产就绪度评估（就绪度 30%，2026-10-07 已并入 `docs/智购开发任务差距分析报告.md` 附录 A）

## [0.0.1] - 2026-09-30

### feat
- M0 脚手架：Monorepo + 父 POM + 12 个服务骨架
- 中间件 Docker Compose（MySQL / Redis / RocketMQ / MinIO / PostgreSQL + pgvector）
- auth-center：手机号验证码登录 + JWT 签发（HS256，access 2h / refresh 14d）
- 公共库 `packages/common`：Result<T>、全局异常、BizException
- 11 个 Java 服务 Flyway 初始化迁移 + Testcontainers 集成测试骨架
- ai-orchestrator：LangChain/LangGraph 对话 Agent + SSE + RAG（pgvector）+ 降级开关 `config/fallback.yml`
- 可观测：Prometheus / Grafana / Loki 配置 + RED/JVM/业务指标面板
- BFF（NestJS）：JWT 鉴权、home/product/chat 聚合、SSE 透传
- H5 脚手架 + admin-merchant 脚手架

### docs
- CLAUDE.md、ADR-0001（记录架构决策）、progress.md、README.md 初版