# 智购・项目进度追踪

> **唯一进度真相**
>
> 。每完成一个可验证单元，更新本文档 + 
>
> `CHANGELOG.md`
>
> ；涉及接口 / 端口变更时同步 
>
> `README.md`
>
>  与 
>
> `docs/README.md`
>
>  索引。
> 状态：✅ 已完成 / 🚧 进行中 / ⬜ 未开始 / ⚠️ 有风险
> 与目标态的差距清单与执行顺序见 
>
> `docs/智购开发任务差距分析报告.md`
>
> 。

## 当前阶段

**M1 交易闭环收尾**（目标态 P0）+ M2 AI 导购已交付。下一步按差距报告 P0 → P1 推进。

## 里程碑



| 里程碑      | 目标                                       | 状态              | 完成日期       |
| -------- | ---------------------------------------- | --------------- | ---------- |
| M0 脚手架   | Monorepo + 中间件 compose + auth-center 能登录 | ✅               | 2026-09-30 |
| M1 交易闭环  | 下单→支付→库存→物流→售后端到端跑通                      | ✅ 全链路联调冒烟 17/17 | 2026-10-07 |
| M2 AI 导购 | SSE 对话 + RAG + 降级兜底 + 对话商品卡              | ✅               | 2026-10-07 |
| M3 大促压测  | 5000 QPS 不垮，P99 < 1.5s                   | ⬜               | -          |
| M4 上线    | 灰度发布 + 监控告警 + 回滚演练                       | ⬜               | -          |

## 已完成

* 2026-10-08：**P0-C 售后逆向全流程闭环（退款真实资金流 + 退回库存联动）**：① payment-service 新增退款表 `payment_refund`（V20261092，refund_no 幂等键）+ `POST /payment/refund`（校验支付单 SUCCESS、金额 0<amount≤实付、沙箱即时 SUCCESS）；② aftersale-service 退款改真实链路（V20261093 加 sku_id/count/refund_no；refund：SELLER_APPROVED→REFUNDING→调 payment→REFUNDED 记 refundNo→调 inventory 回库存，失败告警"待人工补偿"；REFUNDED 幂等）；③ inventory-service 放行 preDeduct/confirm/rollback（此前 HTTP 通道 403，回库存一直靠 MQ 兜底）；④ BFF 新增 aftersale 透传模块（apply/mine/detail/cancel 注入 X-User-Id 头，修复 mine 恒 0 条）。**实测闭环**：¥1 单 apply→approve→refund→REFUNDED（RFF2452BD37CD04379）→payment_refund 落库→**库存自动回滚 99→100**；PENDING 单退款被正确拒绝；H5 售后 tab 7 条 + 详情页状态卡/退款单号展示正确。详见 CHANGELOG [0.1.5]。



* 2026-10-07：**P0-B 交易最终一致性落地（outbox + RocketMQ）**：① order-service OutboxDeliveryTask（30s 周期投递 status=0 → MQ → 置 1，at-least-once）；② ORDER_CLOSED 事件体升级（含 items 明细），取消/超时关单统一新格式；③ inventory-service OrderClosedListener 消费兜底 + ollbackOrder(orderId, items) Redis SETNX 幂等（双通道只释放一次）；④ **RocketMQ broker 地址修复**（Windows Docker Desktop：容器内网 IP 宿主不可达 → -c 强制读取 conf/broker.conf + docker cp 覆盖 rokerIP1=127.0.0.1 + restart，clusterList 已显示 127.0.0.1:10911）。实测：投递 41/41 成功（status 全=1）；同 orderId 双消息幂等闭环——首条释放库存 94→96，次条跳过，只释放一次。详见 CHANGELOG [0.1.4]。\n* 2026-10-07：**P0-A 交易完整性第一梯队落地**：① 超时关单（order-service `OrderTimeoutTask` 每 5 分钟 + 启动首扫，INIT 超 15 分钟→CLOSED + outbox + 释放库存，首扫关闭 16 笔）；② 支付对账补偿（payment-service `notify_status` 迁移 V20261091 + reconcile 补偿 SUCCESS 未通知单 + 运维端点 `/payment/reconcile`，实测补偿 4 笔）。详见 CHANGELOG [0.1.3]。

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



* [ ] 交易最终一致性（RocketMQ 事务消息 + outbox 投递任务）

* [ ] 超时关单 / 支付对账 / 退款资金流

* [ ] 售后全流程 + 凭证上传 MinIO

* [ ] 营销活动 + 凑单最优组合 + 防超卖

* [ ] 评价增量向量更新（pgvector）

* [ ] 用户画像 / 收藏 / 浏览历史 / 会员基础

* [ ] 文件服务增强（压缩 / 转格式 / 鉴权）

* [ ] BFF 补全 + 埋点 + 熔断降级 + SSE 重连

* [ ] 前端售后页 + 商家后台业务页

* [x] ~~Java 服务 JWT 过滤器（11 个）~~（联调确认已存在并生效）

* [ ] 日志脱敏（AOP）

* [ ] Dockerfile（9 个缺失服务）+ 镜像构建

* [ ] CI/CD（build→test→镜像→部署）

* [ ] 全链路压测（k6 阶梯 100→500→1000→2000）

### P1・AI 决策辅助（M2 与产品目标的差距核心）



* [ ] 需求拆解模块（模糊需求 → 预算 / 场景 / 品类 / 偏好结构化）

* [ ] 跨平台比价（外部价格源 + 最优购买方案）

* [ ] 差评分析 / 避坑指南

* [ ] 推荐引擎（行为 + 画像）

* [ ] AI 凑单优化器 / 一键代下单（用户确认流）

* [ ] 会话记忆（多轮持久化 + 会话清空）

* [ ] 推荐理由标准化（可解释性）

### P2+・生态矩阵 / 远期（立项评估后）



* [ ] 社交购物（AI 送礼 / 拼团）/ 内容社区 / 本地生活（新业务域）

* [ ] 商家端 AI 经营体系

* [ ] 智能衣橱 / 家居管理

* [ ] AI 会员订阅体系（¥29 / ¥99 权益）

* [ ] P3-P4：多模态搜款、AR/VR、数字人（无技术底座，先用图像搜款验证）

## 风险与阻塞



| 风险          | 等级 | 说明                                                                                                    |
| ----------- | -- | ----------------------------------------------------------------------------------------------------- |
| 生产就绪度约 40%  | P0 | 全链路已联调跑通（17/17），但 9 个服务无 Dockerfile、无 CI/CD、RocketMQ 事务消息与支付对账仍为骨架 —— 详见 `docs/智购开发任务差距分析报告.md`（附录 A） |
| 压测仅覆盖 BFF 层 | P1 | 50 并发 718 RPS；M3 目标 5000 QPS 需全链路压测 + 瓶颈优化                                                            |

## 变更记录



* 2026-10-07：支付待付款终极根因修复（Security permitAll + BFF 不吞错 + 订单自动联动 PAID + 详情页轮询 + 订单列表筛选修复 + 售后 tab）；浏览器实测支付闭环"商家备货中"；悬浮购物车（414px 容器定位）；真实支付接入指南；全链路联调冒烟 17/17（12 服务 + BFF + H5 全启动，黄金路径全绿，6 处联调 bug 修复）；前端 8 屏对齐 + 交易闭环交付；BFF 六模块透传；order/cart 补口与 bug 修复；文档体系统一（docs/README 索引 + README/CLAUDE/progress 对齐）。

* 2026-10-02：BFF 压测；生产就绪度评估；Mock 数据机制。

* 2026-09-30：项目初始化。