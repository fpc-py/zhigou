# 智购 · 测试问题与 Bug 记录

> 记录测试过程中发现的问题、临时排查命令与修复状态。已修复的条目保留留痕，不删除。
> 状态：🆕 新发现 / 🔧 修复中 / ✅ 已修复 / ⏳ 待验证

## 问题记录

| # | 问题描述 | 涉及端 | 状态 | 备注 |
|---|---|---|---|---|
| 1 | 管理端登录进去后，浏览目录会跳出登录 | admin-merchant | 🆕 | 疑似 token 校验/路由守卫问题，待复现定位 |
| 2 | 需以完整用户视角在浏览器跑一遍全流程 | 全链路 | ⏳ 待验证 | 首页→AI 对话→详情→加购→下单→支付→订单，前端已就绪，待全服务启动联调 |
| 3 | 验证码获取（临时排查命令） | auth-center | ✅ | `docker exec zhigou-redis redis-cli GET "auth:sms:15120598756"` |
| 4 | **CI 集成测试全失败（AuthIntegrationTest HTTP 500）**：① `TEST_JDBC_URL` 键名错误（Spring 不认该键）；② MySQL/Redis 容器无显式等待策略与超时；③ payment/marketing/aftersale 测试类无 Redis 容器；④ 9 服务 `SecurityConfig @Profile("!test")` → test 下默认安全链全拒 401；⑤ cart mock URL/响应脱节、order OutboxDeliveryTask 强依赖 MQ、payment 残留旧迁移、order 断言过时 | 全服务集成测试 | ✅ | ①~③：@DynamicPropertySource 注册标准键 + waitingFor + 120s 超时 + 补 Redis 容器；④：10 服务新建 `TestSecurityConfig`（permitAll，inventory 非 Web 不导入）；⑤：cart mock 对齐、OutboxDeliveryTask `@Profile("!test")`、清理残留迁移、order 改验状态机。**`mvn verify` 全模块 78 项测试 0 失败 0 错误** |
| 5 | **AI 决策辅助工具联调问题**：① `optimize_cart/create_order` 初版入参含 userId，LLM 无法得知真实 ID 会编造（如 U87654321）→ 越权校验拒绝，工具不可用；② 购物车接口 `priceAtAdd=0`（加购未存价格）→ 凑单计算缺价失败；③ create_order 无确认流时 LLM 直接下单有风险 | ai-orchestrator | ✅ | ① 新工具移除 userId 入参，改用服务端 ContextVar 注入当前用户（LLM 无法伪造）；② optimize_cart 逐 SKU 实时查 `product/sku` 现价与规格兜底；③ 工具 docstring 强制确认 + LLM 实测先复述商品金额再等用户确认（确认流通过） |
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