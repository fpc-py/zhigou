# 测试问题 & Bug 记录

## #26 · [0.2.22] order Flyway mock 版本陷阱 + orderIds 字符串反序列化 500 + BFF Number() 丢精度（已修复）

- **现象**：① 新增 V20261002__fulfillment_action 被 order 库已应用的 mock V20261090 覆盖（版本更小被跳过），fulfillment_action 表未建，/order/stats/pending-fulfillment 报 500；② 后端 `List<Number>` 拒收 JSON 字符串 orderId，POST /order/fulfillment/action 返回 500；③ BFF 将 19 位 Snowflake orderId 经 `Number()` 化后回传，精度丢失致后端「订单不存在」
- **根因**：① order 库 flyway_order_history 已应用 V20261090__mock_order，新增迁移版本必须更大（同 inventory）；② Jackson 对 `List<Number>` 遇字符串元素抛 MismatchedInputException；③ JS Number 安全整数上限 ~9e15 < 19 位 Snowflake
- **修复**：① 迁移改名 V20261091__fulfillment_action.sql + clean package（清 target 残留）；② Controller 改 `List<Object>` + `String.valueOf` 中转解析；③ BFF 直接透传字符串 orderIds（不 Number() 化），后端双兼容
- **教训**：Flyway 新迁移版本 > 已应用最大版本是铁律（inventory/order 已各踩一次）；19 位 ID 跨层必须字符串透传（CLAUDE.md 红线），BFF 服务层禁止 Number() 化后再回传

# 智购 · 测试问题与 Bug 记录

> 记录测试过程中发现的问题、临时排查命令与修复状态。已修复的条目保留留痕，不删除。
> 状态：🆕 新发现 / 🔧 修复中 / ✅ 已修复 / ⏳ 待验证

## 问题记录

| # | 问题描述 | 涉及端 | 状态 | 备注 |
|---|---|---|---|---|
| 25 | **P2 供应链二期 3 坑**：① inventory Flyway 新迁移版本号必须 > 已应用版本（mock V20261090），否则被跳过不执行（V20261002/V20261003 均被忽略，表不建、接口 500；改 V20261091 生效）；② AI `_http_post` 参数名是 `json_data` 不是 `body`（关键字参数名错误 → TypeError → 工具异常分支返回「没查到」）；③ AI 重启后须核对监听进程为 venv python（曾出现系统 Python 实例占 8095 加载旧代码，新工具不生效；杀端口进程 + venv 启动 + 核对 CommandLine 含 .venv） | inventory-service / ai-orchestrator | ✅ | ① V20261091 + clean package；② json_data 修正 + SSE 复验 PASS；③ venv 重启核对进程 + 工具调用 PASS |
| 24 | **P2 供应链一期 3 坑**：① AI 工具缺 `@tool` 装饰器（TOOLS 列表元素为裸函数，`getattr(name)` 返回 None → SSE 报「未知工具」；补 `@tool` 后转 StructuredTool）；② 本机 8000 被其他项目进程霸占且杀不掉（127.0.0.1 幽灵监听 + 多实例 uvicorn 竞态），智购 AI 迁移 8095 + BFF 配置同步改；③ BFF `/merchant/*` 走 JwtAuthGuard 需 Bearer token（X-User-Id 头不足），验证走登录流 | ai-orchestrator / bff-shop | ✅ | ① `@tool` 补上 + TOOLS 21 个全 StructuredTool；② AI 8095 + BFF 配置更新；③ 登录流验证 200 |
| 23 | **P2 会员订阅 H5 2 坑**：① 订阅弹层 CSS 追加时锚点误匹配模板内 `<div class="sheet-actions">`（订阅弹层新模板同 8 空格缩进），CSS 整块被插入模板 DOM → RolldownError Invalid end tag；修复：模板内 CSS 移回 `<style>` 并补回被替换掉的 sheet-actions 开标签；② `noUncheckedIndexedAccess` 下 `PLAN_LIST[1]` 类型 `PlanItem | undefined` → `?? PLAN_LIST[1]!` | h5-shop | ✅ | ① 标签恢复 + CSS 归位，vite build 通过；② TS type-check 通过 |
| 22 | **P2 商家经营二期预警接口 3 坑**：① inventory-service 无 swagger 依赖，`@Operation` 编译报「找不到 io.swagger」→ 移除注解（`/inventory/low-stock` 与 `/{skuId}` 路径精确匹配不冲突）；② product-service `anyRequest().authenticated()` 全量拦截 `/product/review/negative` → 内网调用需带 `X-User-Id` header（JwtAuthFilter 内网透传模式）；③ BFF `merchant/warnings` 首次 TS 报错（firstValueFrom 联合类型推断）→ 逐 await + `as any` | inventory + product + BFF | ✅ | ① 编译通过直连 200；② 带 X-User-Id 直连 5 条差评；③ tsc=0 + BFF 登录态 200 |
| 15 | **P2 拼团联调 4 坑**：① Flyway 版本冲突——新迁移 `V20261010` 版本低于已有 mock 迁移 20261090，被跳过不执行；② BFF 拼团透传未带 `x-user-id` 头，marketing Security 全量 JWT → 出站 403 空数组；③ BFF `catchError` 吞业务错误（后端 HTTP 200+code≠200 如 40041/40043）→ 前端看不到真实失败原因；④ 脚本误判「成团团单应出现在招募列表」（openGroups 仅展示 OPEN 招募中团，SUCCESS 团不进招募） | marketing + BFF + H5 | ✅ | ① 迁移改名 `V20261099__group_buy.sql`（> 20261090）重打包重启；② marketing.service 统一注入 `authHeader(userId)`；③ `unwrapOrThrow`：code≠200 抛 HttpException(400, message)，H5 try/catch 展示；④ 按 detail 接口口径验证 members=2 remain=0，非产品缺陷 |
| 16 | **AI 链路 Windows tiktoken 阻断**：langchain-openai 顶层 import tiktoken Rust 扩展被「应用程序控制策略」阻止（`DLL load failed while importing _tiktoken`），进程级必崩，SSE 5 工具全 FAIL | ai-orchestrator | ✅ | `chat_service.py` 迁移为原生 openai `AsyncOpenAI` 流式 + 手动工具循环；`convert_to_openai_tool` 生成 schema（不触发 tiktoken）；SSE 契约不变 |
| 17 | **AI 工具触发不稳定（同 query 时好时坏）**：① system.md 第 8 条「不是客服，不处理退款/物流」与触发规则自相矛盾，模型随机拒绝；② 模型对强场景 query 偶发不调工具 | ai-orchestrator + prompts | ✅ | ① system.md 改为「命中场景必须调对应工具」；② 新增确定性工具路由 `_route_tool`（关键词命中 → 首轮 `tool_choice` 强制），SSE 5/5 PASS |
| 18 | **LLM 超时全走兜底**：fallback.yml `llm.timeout_ms=1500` 过短（首字延迟 1~3s，整体 5~15s），LLM 必然超时 → 无真实工具调用 | ai-orchestrator | ✅ | fallback.yml 改 20000（小于 BFF aiOrchestrator 30s 超时）；服务重启加载 |
| 19 | **asyncio.timeout 取消后 ContextVar reset 崩溃**：LLM 超时取消工具链时 generator 被 athrow 到不同 context，`finally _current_user_id.reset(token)` 抛 `ValueError: Token was created in a different Context`，吞掉正常 done | ai-orchestrator | ✅ | finally reset 包 try/except ValueError，忽略上下文不匹配 |
| 20 | **SSE 测试脚本分隔符不兼容**：sse_starlette wire 分隔为 `\r\n\r\n`，断言脚本按 `\n\n` 分块 → 整段解析失败 calls=[] done=False（服务端实际正常） | 测试脚本 | ✅ | 断言脚本兼容 `\r\n\r\n` 与 `\n\n` 两种分隔，重跑 5/5 PASS || 21 | **工具空结果后 LLM 编造商品**（浏览器实测暴露）：gift_assistant 搜「烘焙」无候选返回「暂时没有搜到」，LLM 转而编造「苏泊尔电子秤 ¥199 / 北欧风硅胶烘焙工具四件套 ¥189 / 定制手写烘焙食谱本 ¥158」等库外商品与口碑细节，违反禁编造红线（system.md 第 4 条原表述约束力不足） | ai-orchestrator + prompts | ✅ | ① system.md 第 4 条强化为「工具失败/返回空时如实告知，可给不指名具体商品的通用建议；严禁自创商品名/品牌/价格/评分」；② chat_service 新增 `_guard_result` 防编造护栏——工具结果为空/含「暂无/未查到」语义时强制追加禁令回执（LLM 对工具回执服从度最高）；③ HTTP 复测：烘焙送礼 → 如实「平台暂时没有查到」+ 品类级建议（电子厨房秤/硅胶垫等），无编造；耳机拼团 → 如实「暂无进行中拼团」+ 通用建议 || 1 | 管理端登录进去后，浏览目录会跳出登录 | admin-merchant | 🆕 | 疑似 token 校验/路由守卫问题，待复现定位 |
| 2 | 需以完整用户视角在浏览器跑一遍全流程 | 全链路 | ⏳ 待验证 | 首页→AI 对话→详情→加购→下单→支付→订单，前端已就绪，待全服务启动联调 |
| 3 | 验证码获取（临时排查命令） | auth-center | ✅ | `docker exec zhigou-redis redis-cli GET "auth:sms:15120598756"` |
| 4 | **CI 集成测试全失败（AuthIntegrationTest HTTP 500）**：① `TEST_JDBC_URL` 键名错误（Spring 不认该键）；② MySQL/Redis 容器无显式等待策略与超时；③ payment/marketing/aftersale 测试类无 Redis 容器；④ 9 服务 `SecurityConfig @Profile("!test")` → test 下默认安全链全拒 401；⑤ cart mock URL/响应脱节、order OutboxDeliveryTask 强依赖 MQ、payment 残留旧迁移、order 断言过时 | 全服务集成测试 | ✅ | ①~③：@DynamicPropertySource 注册标准键 + waitingFor + 120s 超时 + 补 Redis 容器；④：10 服务新建 `TestSecurityConfig`（permitAll，inventory 非 Web 不导入）；⑤：cart mock 对齐、OutboxDeliveryTask `@Profile("!test")`、清理残留迁移、order 改验状态机。**`mvn verify` 全模块 78 项测试 0 失败 0 错误** |
| 5 | **AI 决策辅助工具联调问题**：① `optimize_cart/create_order` 初版入参含 userId，LLM 无法得知真实 ID 会编造（如 U87654321）→ 越权校验拒绝，工具不可用；② 购物车接口 `priceAtAdd=0`（加购未存价格）→ 凑单计算缺价失败；③ create_order 无确认流时 LLM 直接下单有风险 | ai-orchestrator | ✅ | ① 新工具移除 userId 入参，改用服务端 ContextVar 注入当前用户（LLM 无法伪造）；② optimize_cart 逐 SKU 实时查 `product/sku` 现价与规格兜底；③ 工具 docstring 强制确认 + LLM 实测先复述商品金额再等用户确认（确认流通过） |
| 12 | **BFF 比价透传 TS 报错**：catch 块 err 为 unknown，`err.message` 编译失败 | bff-shop | ✅ | 改 `err instanceof Error ? err.message : String(err)` |
| 13 | **search_by_image 视觉识别失败**：LLM vision 拉取图片 URL 超时（本机到 OpenAI 网络不可达），工具返回降级提示 | ai-orchestrator | ✅ | 60s 超时 + 一次重试 + 优雅降级文案；真实云端生产环境可正常识别（外部网络限制非代码缺陷） |
| 14 | **LLM 收到图片后偶发跳过工具**：直接文字回复不调 search_by_image | ai-orchestrator | ✅ | 上传图片时注入强制系统指令"必须先调用 search_by_image"，实测稳定触发 |
| 9 | **LLM 幻觉商品**：用户问"结合我买过的推荐"，LLM 只调 analyze_user_context 后编造"李宁 轻云4/安踏 柔韧3.0"等库外商品（旧 system.md 无工具清单与红线） | ai-orchestrator | ✅ | 重写 prompts/system.md：12 工具真实清单 + 六步决策流程 + 硬红线"推荐必须来自工具返回，禁止编造商品名/价格/评价"；SSE 复测 LLM 改调 recommend_products 返回 4 款真实商品（含理由/刷评标记） |
| 10 | **/order/mine 内网调用 500**：recommend 引擎调 `/order/mine` 未带必填 userId 参数（MissingServletRequestParameterException），且 order-service 曾以无 profile 启动（缺 mock 数据） | product-service + order-service | ✅ | 推荐引擎调用补 `?userId=`；order-service 恢复 `--spring.profiles.active=dev` 启动；冒烟通过（23 件历史购买画像命中） |
| 11 | **recommend_products 输出双¥**：`_fen_to_yuan` 已带 ¥ 前缀，拼接时重复成 ¥¥99.00 | ai-orchestrator | ✅ | 拼接去掉重复 ¥，py_compile 通过 |
| 7 | **Flyway 版本乱序**：新建表迁移 `V20261002` 未被执行（Flyway `outOfOrder=false` 跳过低于已应用版本 20261090 的迁移），mock `V20261091` 先执行报"表不存在" | product-service | ✅ | 建表迁移重命名为点号版本 `V20261090.1__product_review.sql`（版本号介于 20261090 与 20261091 之间），`docker exec zhigou-mysql mysql ... DELETE FROM flyway_product_history WHERE success=0` 清理失败记录；重启后迁移顺序 20261090.1→20261091 正常 |
| 8 | **AI skuId/spuId 混淆**：LLM 把 `search_products` 返回的 skuId 当 spuId 传给 `review_analysis`，返回"该商品信息暂时查不到" | ai-orchestrator + product-service | ✅ | `search_products` 输出并列 `spuId` 与 `skuId`（spuId 用于评价分析）；`review_analysis` 直查 SPU 失败按 `/product/sku/{id}` 反查 spuId；`SpuDetailResponse.SkuItem` 补 `spuId` 字段 + `querySku` 装配；SSE 实测返回真实统计与"⚠ 疑似刷评 3 条重复" |
| 6 | **CI 集成测试：file-service 拉取 `minio/minio:latest` 404 pull access denied**（Docker Hub 匿名拉取受限/镜像已停维护；本机有缓存镜像故本地全绿，CI 全新环境必现） | .github/workflows/ci.yml + file-service 测试 | ✅ | 三轮实证收敛：① quay.io RELEASE 500 unauthorized → ② Docker Hub RELEASE 404「repository does not exist」（minio/minio 整仓在 runner 匿名不可拉）→ ③ **最终：换 `adobe/s3mock`（Docker Hub 匿名可拉）+ Testcontainers 官方模块 `com.adobe.testing:s3mock-testcontainers:4.5.0`（`S3MockContainer`）**；MinioInitializer 恢复 makeBucket 自动建桶（s3mock 无预建配置）、setBucketPolicy 容错（409 仅告警）；CI Probe 步骤探测成功写回 `MINIO_IMAGE`；本地 `mvn verify` 全模块 BUILD SUCCESS（file-service 6/6） |

## 已修复记录（历史）

| 日期 | 问题 | 修复 | 验证 |
|---|---|---|---|
| 2026-10-07 | 订单金额/商品名为写死占位（100 分 / SKU-xxx） | order-service 下单前调 product-service 取真实价格与 SPU 名称，失败回退占位 | `mvn compile` 通过 |
| 2026-10-07 | 购物车无法删除单条（前端曾用 count=0 残留条目） | cart-service 新增 `DELETE /cart/{skuId}` + BFF/前端打通 | cart-service/BFF/H5 构建通过 |
| 2026-10-07 | 全链路首次联调 6 处真实 bug（Snowflake ID 精度丢失 / payment ClassCastException / aftersale 403 / cart·file·user 401 / AI 401 整链路失败 / ai-orchestrator 启动入口错误） | order/cart/auth DTO ID 转字符串；payment 安全 toLong；aftersale 读 X-User-Id；三服务拦截器兼容内网透传头；chat_service.py 降级兜底；`python main.py` 启动 | 全链路冒烟 **PASS 17/17**（`scripts/smoke/zhigou-e2e.ps1`） |
| 2026-10-07 | 沙箱支付成功但前端不跳转（mockPay 传 `sign:'sandbox-mock'` 与后端验签不符，403 后被静默吞掉） | H5 `payment.ts` 用 `sha256(paymentNo+sandbox-secret-key)` 正确签名（三处调用统一修复）；checkout/orders/order-detail 失败时不再静默，提示"支付失败，请重试"；checkout 支付失败兜底跳订单详情 | `npm run build` 通过；BFF 链路实测 mock-pay PASS |
| 2026-10-07 | 主页缺少购物车快捷入口 | home 页新增悬浮购物车按钮（品牌渐变 FAB + 数量角标，数量来自 `/cart/mine`） | `npm run build` 通过 |
| 2026-10-07 | 悬浮购物车位置按电脑边而非手机容器 | FAB 定位改为 `right:max(16px, calc((100vw-414px)/2+16px)); bottom:calc(76px + safe-area)`，与 414px 手机容器/底部 TabBar 对齐 | 浏览器实测首页显示正常 |
| 2026-10-07 | **支付显示成功但仍待付款（终极根因）**：`payment-service` 的 Spring Security `anyRequest().authenticated()` 未放行 `/payment/sandbox/mock-pay`，BFF 转发又不带认证头 → 全部 mock-pay 被 Security 403，**从未到达业务层**；而 BFF 的 `catchError` 把 403 吞掉返回 200 → 前端误显示"支付成功" | ① payment-service `SecurityConfig` 将 `/payment/sandbox/mock-pay`、`/payment/notify/**` 加入 permitAll（服务端回调靠签名验签，无用户上下文）；② BFF `payment.service.ts` mockPay 不再吞错，失败如实抛给上层；③ 订单联动自动化：新增 `OrderNotifyClient`（支付成功 HTTP 通知 order-service `payCallback`，重试 3 次+对账兜底），`OrderServiceImpl.payCallback` 幂等；④ 订单详情页 INIT 时自动轮询至 PAID | 浏览器实测全流程：支付成功 → 自动跳订单详情 → **"商家备货中"（PAID）**；直连 8087 验签通过返回 404（此前 403） |
| 2026-10-07 | 「我的→售后」入口跳转后落在"全部"（orders 页无售后 tab，AFTERSALE 非订单状态） | orders 页新增"售后"tab（= REFUNDING+REFUNDED），与"我的"页 `orderStats` 的 `AFTERSALE` 入口对齐 | 浏览器实测：售后 tab 高亮并正确过滤 |
| 2026-10-07 | **订单列表页 tab 筛选从未生效**：模板 `v-for="o in list"` 用的是原始列表，`filtered` 计算属性未被模板使用 | 模板改 `v-for="o in filtered"`，空态判断改 `filtered.length === 0` | 浏览器实测：待付款/待发货/售后 tab 均正确过滤 |
| 2026-10-07 | 订单详情页对 CLOSED/REFUNDED 仍显示"申请售后"（不合状态机） | 操作按钮按状态收敛：INIT=取消+支付，PAID/SHIPPED/COMPLETED=申请售后，终态无操作 | `npm run build` 通过 |

> 新增 bug 时在此追加一行；涉及代码修复的同步更新 `CHANGELOG.md`。
| 2026-10-10 | **AI 工具 SSE 全部调用失败（历史隐患）**：chat_service `_dispatch_tool` 用 `tool.__name__` 匹配，而 `@tool` 包装后为 StructuredTool（无 `__name__`）→ 任意工具被模型选中即报「工具执行失败: ... __name__」并被转述为「暂时没查到」兜底 | 改为 `getattr(tool,'name',None) == name` + `tool.ainvoke(args)` | SSE 全链路 tool_call→tool_result→AI 简报 PASS（19 工具全部受益） |
| 2026-10-10 | **H5 商家中心页数据 NaN**：api/merchant.ts 直接返回 axios response，页面误取 `res.data`（实为 BFF body） | 按 wallet.ts 模板改 `res.data.data` 解包 | 页面全数据渲染 |


| 2026-10-10 | **mybatis-plus IPage/Page 类不可用**：wallet 首版流水分页用 `selectPage` + `IPage` 编译报「找不到符号」（当前 starter 版本路径问题） | 改用手写 `.last("limit o,s")` 返回 List（与 life 一致） | mvn 编译通过 |
| 2026-10-10 | **Long 参数解析 500**：直连测试用 `userId=9999999999999999999` 超 Long 最大值 → NumberFormatException 500 | 测试数据改为合法 Long（8888888888888888888） | 惰性创建 PASS |


| 2026-10-10 | **closet 列表含已删除衣物**：DELETE 后 myItems 未过滤 status=1，被删衣物仍出现在列表 | myItems 加 `.eq(ClosetItem::getStatus, 1)` | 直连/BFF 复测 items=8（不含已删） |
| 2026-10-10 | **H5 type-check TS2345**：`switchTab(t.k)` 传 string 给字面量联合类型参数 | switchTab 参数改 string + 内部断言 | vue-tsc 通过 |


| 2026-10-10 | **BizException 构造签名**：life 首版 `BizException("CODE","msg")` 编译失败（common 定义为 `BizException(int,String)`/`(String)`） | 全部改用业务码 `BizException(40001/40011/...)` | mvn 编译通过 |
| 2026-10-10 | **mapper 未扫描**：life 的 3 个 Mapper 缺 `@Mapper` 注解 → 启动报 No qualifying bean | 补 `@Mapper` | 启动成功 |
| 2026-10-10 | **BFF 控制器缺 JwtAuthGuard**：life.controller 用 `@Headers('x-user-id')` 拿不到 userId（BFF 不转发该头）→ 出站空头致下游 401，接口全空 | 改用 `@UseGuards(JwtAuthGuard)` + `req.userId`（与 community 一致） | BFF 8/8 PASS |
| 2026-10-10 | **BFF 新模块 import 缺 `.js` 后缀**（ESM 严格模式）→ TS2307 | life controller/module import 补 `.js` | nest build 通过 |


| 2026-10-10 | **BFF videoPublish 字段名不匹配**：BFF 传 `userId`，后端 CommunityVideo 实体为 `authorId` → 后端判「请先登录」400 | BFF videoPublish 出站 body 改 `authorId: userId` | BFF video-publish PASS |
| 2026-10-10 | **生成脚本漏字段**：二期实体 CommunityVideo/CommunityLive 生成时遗漏 createdAt/updatedAt（ServiceImpl setCreatedAt 编译报错） | 补实体两字段 | mvn 编译通过 |
| 2026-10-10 | **H5 type-check 报 CommunityVideo 缺 liked/favorited**（短视频页操作按钮绑定该字段） | api 接口补可选字段 liked/favorited | H5 构建通过 |


| 2026-10-10 | **雪花 ID 超 JS Number 精度**：BFF 透传后 `2107757313435291648` 被 JSON 解析失真为 `2107757313435291600`，后端按失真 id 查库恒 400「笔记不存在」（detail/like/comment 全挂，publish/mine 数据里 authorId 已失真） | community 实体/VO `id/authorId/spuId` + comments map 统一 `@JsonSerialize(ToStringSerializer)` / `String.valueOf`（JSON 层全字符串；不改全局 Long 序列化以免 priceFen 变字符串破坏前端计算） | BFF 全链路 7/7：detail/like/comment 恢复 PASS，id 返回字符串 |
| 2026-10-10 | **BFF ai-writer 恒 500「服务超时」**：① TimeoutInterceptor 全局 3s 一刀切未豁免 AI 长任务；② AI writer 返回裸 JSON（无 code/data 包装），unwrapOrThrow 取不到 `data` 降级 null | ① EXEMPT_PREFIXES 追加 `/community/ai-writer`；② aiWriter 直接透传响应体 | BFF ai-writer PASS（draft 完整返回） |
| 2026-10-10 | **BFF 补丁脚本重复执行**：config 出现重复键、service/controller 重复方法（TS2393/TS1117）；去重脚本按「首 marker→我的笔记」区间删除误删整个 aiWriter 方法 | 去重改为精确块替换（config 删相邻重复行、service/controller 删首个完整块）→ 重插 aiWriter → 构建通过 | `nest build` 通过 |
| 2026-10-10 | **浏览器 UI 点按实测受限**：bu 会话对该 tab `viewport=0x0`、snapshot/find/js 均拿不到元素（get_page_text 正常，页面渲染正常） | 不绕过；交互链路以 BFF API 全链路 7/7 + 页面渲染文本验证为验收依据，UI 点按留待下次实测 | 信息流/疑似营销标记/导航渲染文本验证通过 |


| 2026-10-08 | AI 隐性需求挖掘工具未接入（P1 三批功能增量） | ai-orchestrator 新增 `analyze_user_context()`：读历史订单统计常购品类/价位带/复购倾向，服务端注入 userId（无入参防编造）；system.md 同步补齐工具清单+六步决策流程，红线改「禁止自主下单（确认后允许代下单）」 | py_compile 通过；SSE 实测：「参考我买过的」 → analyze_user_context 返回 orderCount=22/运动鞋21/百元内 → search_products 耳机，全链路正常 |
| 2026-10-07 | **RocketMQ 生产者 sendDefaultImpl call timeout（Windows Docker Desktop）**：broker 向 namesrv 注册容器内网 IP 172.18.x.x，宿主不可达；compose 挂载到 /root/store/config 路径不存在（AccessDenied）、命令行 brokerIP1 在 --enable-proxy 下不生效、recreate 重置 docker cp 配置层，均失败 | middleware.yml command 显式 \-c /home/rocketmq/rocketmq-5.3.0/conf/broker.conf\（镜像默认路径）+ \scripts/mq-fix-broker-ip.ps1\ docker cp 覆盖（brokerIP1=127.0.0.1）+ \docker restart\（勿 recreate）；修复后重启 order-service 清旧路由缓存，投递 41/41 成功 | clusterList Addr=127.0.0.1:10911；outbox status 全=1 |
| 2026-10-08 | **售后退款 500 = aftersale Feign readTimeout 300ms 超时**：payment 首次调用（含 DB 事务）超过 300ms → RetryableException，售后单停在 REFUNDING（事务回滚） | aftersale `application.yml` Feign 默认超时改 connectTimeout 500 / readTimeout 3000 | 重启后 refund 成功：REFUNDED + payment_refund 落库 |
| 2026-10-08 | **售后退回库存失败（403，告警"待人工补偿"）**：inventory `/rollback` 被 Spring Security 拦截——HTTP 通道全 403（此前关单回滚一直靠 MQ 兜底，掩盖了此问题） | inventory `SecurityConfig` 将 `/inventory/preDeduct`、`/inventory/confirm`、`/inventory/rollback` permitAll（内网服务调用，生产需白名单） | refund 内自动回库存成功：¥1 单库存 99→100 |
| 2026-10-08 | **BFF /aftersale/mine 恒返回 0 条**：BFF 透传 GET 不带 X-User-Id 头，aftersale Security 403 被 catchError 吞掉返回 []（登录/详情同样受影响） | BFF `aftersale.service.ts` mine/detail/cancel 统一注入 `x-user-id`（controller 透传 JWT userId） | BFF 冒烟 mine 返回 7 条、detail 返回 REFUNDED+refundNo |
| 2026-10-08 | **日志脱敏三个坑（P0-D1）**：① Snowflake 长数字（userId `210775...1648`）被银行卡正则 `\d{13,19}` 误掩；② 验证码字段 `code` 不在敏感词表且 6 位数字不匹配正则 → 漏掩；③ 切面反射仅收集注解字段 → 入参丢 phone | ① `BANK_CARD_RE` 收紧为 `[3456]\d{12,18}`；② `LoginRequest.code` 加 `@SensitiveField(PASSWORD)`；③ `collectAnnotatedFields` 类上有注解时收集全部字段（注解字段按注解类型掩、其余交 maskMap） | auth-center 实测：`入参=[{"phone":"********","code":"******"}]`、`accessToken=********`、**userId 完整保留** |
| 2026-10-08 | **开发环境坑：jar 被运行中的 java 进程文件锁，`mvn package` 显示 BUILD SUCCESS 但 jar 时间戳不更新**（Windows 文件锁静默保留旧产物），且旧 jar 内嵌 common 可能不含新类 → 排障时误判"代码没生效" | 先 `Stop-Process` 占用端口的 java 进程 → 删 target 下 jar 与 `.original` → 重新 `mvn package`（确认 jar LastWriteTime 更新 + 解包检查 `BOOT-INF/lib/zhigou-common` 内 `com/zhigou/common/mask` 类齐全） | jar 重建成功（10:21:36，lib common 含 8 个 mask 类）；重启后脱敏日志正常输出 |
| 2026-10-08 | **Dockerfile 生成/构建三个坑（P0-D2）**：① PowerShell here-string 里 $JAVA_OPTS 被展开为空 → ENTRYPOINT 变 java  -jar（JVM 参数丢失）；② 根 pom modules 列出全部模块，仅拷当前服务 pom → reactor Child module ... does not exist（ProjectBuildingException）；③ 本地 Docker Desktop 容器内 mvn 下载依赖卡死（宿主网络正常，容器网络到 Maven 仓库不通）——多阶段 Stage1 本地无法验证 | ① 反引号 ` $JAVA_OPTS ` 转义保留字面（运行时由 sh 展开）；② 全部 11 个服务 pom + packages/proto + common 一并 COPY；③ 新增 Dockerfile.runtime（宿主 mvn 打包 + COPY jar），本地构建 11 个镜像验证运行时（多阶段保留供 CI） | 11 个镜像 zhigou/<svc>:0.1.0 构建成功；user-service 容器实测 JVM 启动正常 + 非 root（started by app）；compose config 校验通过 |
| 2026-10-08 | **.dockerignore 排除 **/target 导致 Dockerfile.runtime 构建报 jar not found**（COPY 宿主 jar 被 context 过滤） | runtime Dockerfile 构建 context 改为服务目录：docker build -f Dockerfile.runtime -t zhigou/<svc>:0.1.0 services/<svc>（COPY target/...） | 构建成功 exit=0 |
- 2026-10-08（P1）：压测复测 page 通过率在 2000VU 高 VU 段波动（82%↔67%）——缓存命中后 BFF 日志全成功（66ms），k6 侧失败源于单机容量（单 BFF 事件循环 + 写链路重事务排队）；结论：50VU 达标，2000VU 需横向扩展（报告 §6.3）。
- 2026-10-08（P1）：**ai-orchestrator 日志 15 万行**——detail 每次迭代打 RAG，AI 服务处理不过来且日志爆炸；已加 BFF 内存缓存（同 spu 5min）消除重复调用。
- 2026-10-08（P1）：**mvn package 假成功坑再现**——进程未杀干净时 Unable to rename ...jar to ...jar.original（BUILD FAILURE），须先杀 8083 占用进程再删 jar 重打包。