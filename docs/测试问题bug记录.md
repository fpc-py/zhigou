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

> 新增 bug 时在此追加一行；涉及代码修复的同步更新 `CHANGELOG.md`。
