# 智购 · 变更记录（CHANGELOG）

> 每个可交付单元（功能/修复/重构/文档）在此登记，格式遵循 [Keep a Changelog](https://keepachangelog.com/zh-CN/1.1.0/) 与 [语义化版本](https://semver.org/lang/zh-CN/)。
> 格式：`[类型] 模块：描述`。类型：feat / fix / refactor / test / docs / chore。

## [Unreleased]

### P0 · M1 收尾（进行中）
- 营销活动（满减/秒杀/拼团/凑单）
- 评价增量向量更新
- 用户画像 / 收藏 / 浏览历史
- CI/CD、全链路压测

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