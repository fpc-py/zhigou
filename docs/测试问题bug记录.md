# 智购 · 测试问题与 Bug 记录

> 记录测试过程中发现的问题、临时排查命令与修复状态。已修复的条目保留留痕，不删除。
> 状态：🆕 新发现 / 🔧 修复中 / ✅ 已修复 / ⏳ 待验证

## 问题记录

| # | 问题描述 | 涉及端 | 状态 | 备注 |
|---|---|---|---|---|
| 1 | 管理端登录进去后，浏览目录会跳出登录 | admin-merchant | 🆕 | 疑似 token 校验/路由守卫问题，待复现定位 |
| 2 | 需以完整用户视角在浏览器跑一遍全流程 | 全链路 | ⏳ 待验证 | 首页→AI 对话→详情→加购→下单→支付→订单，前端已就绪，待全服务启动联调 |
| 3 | 验证码获取（临时排查命令） | auth-center | ✅ | `docker exec zhigou-redis redis-cli GET "auth:sms:15120598756"` |

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
| 2026-10-07 | **RocketMQ 生产者 sendDefaultImpl call timeout（Windows Docker Desktop）**：broker 向 namesrv 注册容器内网 IP 172.18.x.x，宿主不可达；compose 挂载到 /root/store/config 路径不存在（AccessDenied）、命令行 brokerIP1 在 --enable-proxy 下不生效、recreate 重置 docker cp 配置层，均失败 | middleware.yml command 显式 \-c /home/rocketmq/rocketmq-5.3.0/conf/broker.conf\（镜像默认路径）+ \scripts/mq-fix-broker-ip.ps1\ docker cp 覆盖（brokerIP1=127.0.0.1）+ \docker restart\（勿 recreate）；修复后重启 order-service 清旧路由缓存，投递 41/41 成功 | clusterList Addr=127.0.0.1:10911；outbox status 全=1 |
| 2026-10-08 | **售后退款 500 = aftersale Feign readTimeout 300ms 超时**：payment 首次调用（含 DB 事务）超过 300ms → RetryableException，售后单停在 REFUNDING（事务回滚） | aftersale `application.yml` Feign 默认超时改 connectTimeout 500 / readTimeout 3000 | 重启后 refund 成功：REFUNDED + payment_refund 落库 |
| 2026-10-08 | **售后退回库存失败（403，告警"待人工补偿"）**：inventory `/rollback` 被 Spring Security 拦截——HTTP 通道全 403（此前关单回滚一直靠 MQ 兜底，掩盖了此问题） | inventory `SecurityConfig` 将 `/inventory/preDeduct`、`/inventory/confirm`、`/inventory/rollback` permitAll（内网服务调用，生产需白名单） | refund 内自动回库存成功：¥1 单库存 99→100 |
| 2026-10-08 | **BFF /aftersale/mine 恒返回 0 条**：BFF 透传 GET 不带 X-User-Id 头，aftersale Security 403 被 catchError 吞掉返回 []（登录/详情同样受影响） | BFF `aftersale.service.ts` mine/detail/cancel 统一注入 `x-user-id`（controller 透传 JWT userId） | BFF 冒烟 mine 返回 7 条、detail 返回 REFUNDED+refundNo |
