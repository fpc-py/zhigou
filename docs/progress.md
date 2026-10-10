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

## 2026-10-10 · 上线就绪文档（M4 对齐）

| 项 | 状态 | 说明 |
|---|---|---|
| release-checklist.md | ✅ 改写 | 对齐真实部署资产：去掉 Nacos/K8s 残留，改为 compose 蓝绿 + middleware/services/monitoring 三编排；保留启动顺序/回滚条件/性能基准/签署结构 |
| gray-release.md | ✅ 新建 | 灰度发布（服务分 A/B/C 三批放量 + 决策门）+ 回滚方案（镜像回退首选 / DB 回滚高风险流程 / Hotfix） |
| monitoring-alerting.md | ✅ 新建 | RED/USE 指标体系、SLO、P1-P3 告警定级 + PromQL 规则示例、值班 SOP、日志检索 |
| docs/README.md 索引 | ✅ 更新 | 运维域登记 gray-release / monitoring-alerting（🆕） |
| 运行态验证 | ⏸ 未测试 | 纯文档交付（引用路径/端口已静态核验），未 git 提交，随攒批统一提交 |
| ✅ 部署资产整改 | ✅ 已完成 | services.yml 镜像改为 ${ZHIGOU_REPO:-zhigou}/<svc>:${TAG:-0.1.0} 占位（11 服务）；deploy.yml 注入 ZHIGOU_REPO=ghcr.io/fpc-py/zhigou；compose config 双模式渲染验证通过（默认 zhigou/0.1.0、生产 ghcr.io/fpc-py/zhigou/main-<sha>）；release-checklist/gray-release 前置条件同步更新。未测试未提交 |

## 2026-10-08 · P1 第六批：跨平台比价 + 图片搜款（多模态）

| 项 | 状态 | 说明 |
|---|---|---|
| 跨平台比价 | ✅ | /price/compare 适配器聚合京东/天猫/拼多多，含运费/到货/最优标记/建议，冒烟通过；AI compare_prices 升级为跨平台输出 |
| 图片搜款 | ✅ | search_by_image 工具（vision 特征 → 真实搜索）+ imageUrl 全链路（H5→BFF→AI）+ 对话页相机入口；触发稳定，视觉识别外部网络不可达时降级提示 |
| 前端接通 | ✅ | 比价页删除模拟数据改真实接口；对话页图片上传/预览/发送 |
| 修复 | ✅ | vision 60s 超时+重试；上传图片强制走 search_by_image 指令 |
| 限制 | ⚠ | 渠道价为本地模拟源（生产换真实 API）；vision 依赖 LLM 端点网络（本机不可达时降级） |

## 2026-10-08 · P1 第五批：个性化推荐引擎（画像 + 评分 + AI 工具 + 前端）

| 项 | 状态 | 说明 |
|---|---|---|
| 推荐引擎 | ✅ | product-service `/recommend`：订单品类/价位带 + 购物车意向 + 评价口碑加权评分，可解释理由 + 刷评/已购/小众标记，API 冒烟通过 |
| AI 工具 | ✅ | `recommend_products` 接入对话，SSE 实测 analyze_user_context→recommend_products 全链路无编造 |
| 提示词防幻觉 | ✅ | system.md 重写：12 工具清单 + 六步流程 + 禁编造红线（原 LLM 曾编造库外商品名） |
| BFF + H5 | ✅ | /home/feed 聚合 personal 字段；首页"猜你喜欢 · 为你定制"区块，双端 build 通过 |
| 修复 | ✅ | /order/mine 缺 userId 参数 500（补参）；order-service 恢复 dev profile（mock 数据）；双¥ 去重 |

## 2026-10-08 · P1 第四批：评价数据底座 + 差评/水军识别

| 项 | 状态 | 说明 |
|---|---|---|
| 评价数据底座 | ✅ | product-service 新增 `product_review` 表 + 发表/列表/统计三接口 + dev mock 25 条（含差评与刷评特征），API 冒烟通过 |
| AI 评价分析 | ✅ | `review_analysis` 升级为真实评价分析：统计+差评要点+规则化识别（疑似刷评/差评比例/口碑），SSE 实测通过 |
| skuId/spuId 混淆 | ✅ | `search_products` 输出并列 spuId/skuId，`review_analysis` 兼容 skuId 反查，后端 SkuItem 补 spuId |
| Flyway 版本乱序 | ✅ | 建表迁移改点号版本 `V20261090.1`（介于 20261090/20261091），清理 failed 记录，顺序恢复 |

## 2026-10-08 · CI 集成测试基建闭环（MinIO/s3mock + RocketMQ 条件化）

| 项 | 状态 | 说明 |
|---|---|---|
| file-service 集成测试存储镜像 | ✅ | `minio/minio`（Docker Hub 整仓 404 / quay 500）→ `adobe/s3mock` + Testcontainers 官方模块 `s3mock-testcontainers:4.5.0`；本地 `mvn verify` 全模块 SUCCESS，CI 13 checks 全绿 |
| inventory 测试 RocketMQ 依赖 | ✅ | `OrderClosedListener` 加 `@ConditionalOnProperty` 条件开关，test profile 禁用——CI 无 MQ 环境上下文可正常启动，本地/CI 均 hermetic |
| CI 流水线 | ✅ | GitHub Actions 13/13 checks passed（build-test 67 项测试 + 11 服务镜像构建） |

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

* 2026-10-08：**P0-D1 日志脱敏 AOP（生产非功能第一块）**
* 2026-10-08：**P0-D2 容器化部署能力落地（生产非功能第二块）**
* 2026-10-08：**P0-D3 CI/CD 流水线（生产非功能第三块）**
* 2026-10-08：**P0-D4 全链路压测（生产非功能第四块）**
* 2026-10-08：**P1 性能优化落地（压测瓶颈修复）**：product/page Redis 缓存（TTL300s，命中 15x）+ HikariCP 50 + BFF keep-alive 连接池 + RAG 300ms 快速失败 + RAG 结果缓存；复测 ① 错误率 35.65%→5.82%、P95 10.2s→8.1s、page 33%→82%；② QUICK 50VU **0 错误 P95 329ms 达标**；2000VU 未达标为单机容量（生产化建议见报告 §6.3）。详见 CHANGELOG [0.2.0]。：k6 脚本 \scripts/load-test/full-chain-load-test.js\（黄金路径读 80%/写 20%、阶梯 100→2000VU、QUICK 冒烟模式）；基线 50VU 0 错误 P95 213ms；全量 52763 请求错误率 35.65%（product/page 33% 通过为瓶颈）+ 优化建议 P1（Redis 缓存/BFF 连接池/RAG 快速失败）。报告 \docs/load-test/全链路压测报告.md\。详见 CHANGELOG [0.1.9]。：\.github/workflows/ci.yml\（push main/PR/tag 触发；build-test JDK17+mvn verify 全模块单测；build-images 矩阵 11 服务多阶段 Dockerfile 构建 + GHCR 推送，tag 策略 main-/latest/版本；gha 层缓存；多阶段 Stage1 在 CI 补验证）+ \.github/workflows/deploy.yml\（手动触发，SSH + compose pull && up + 逐服务健康检查，secrets 校验）。YAML 校验通过。详见 CHANGELOG [0.1.8]。：① 11 服务生产级多阶段 Dockerfile（Stage1 maven:3.9-eclipse-temurin-17 容器内编译——先拷全部 POM 复用层缓存再 -am 打包；Stage2 eclipse-temurin:17-jre 非 root（uid 1001）+ G1GC JVM 参数 + EXPOSE + HEALTHCHECK /actuator/health）；② 根 .dockerignore 压缩构建上下文；③ 11 个运行时镜像 zhigou/<svc>:0.1.0 构建成功，user-service 容器实测 JVM 17 启动正常、非 root（started by app）；④ 生产编排 infra/compose/services.yml（compose_default 网络 + 环境变量覆盖 DB/Redis/MQ/服务间 URL + restart 自愈，config 校验通过）。踩坑： here-string 转义、reactor 需全模块 POM、本地容器内 mvn 网络不通（runtime Dockerfile 绕过）。详见 CHANGELOG [0.1.7]。：packages/common 新增 mask 模块（SensitiveType/MaskUtil/SensitiveLog/SensitiveLogAspect/MaskAutoConfiguration），@SensitiveLog 注解 + 序列化后递归掩码（字段名敏感词 + 正则智能识别 + @SensitiveField 注解兜底，失败降级 toString，绝不抛异常影响主流程）；auth-center login/send-sms-code 接入。**实测**：登录日志 `phone=******** / code=****** / accessToken=********`，**Snowflake userId 完整保留不误掩**（银行卡正则收紧为 [3456] 开头）；MaskUtilTest 11/11。详见 CHANGELOG [0.1.6]。
* 2026-10-08：**CI 修复：file-service MinIO 镜像拉取 404**：Docker Hub `minio/minio:latest` 已停维护/匿名拉取受限 → 默认改 `quay.io/minio/minio:RELEASE.2024-11-07T00-52-20Z`（固定版）+ `MINIO_IMAGE` 环境变量可覆盖 + 启动等待策略；本地 6/6 测试全绿（CHANGELOG [0.2.5]）。
* 2026-10-08：**P1 AI 决策辅助第二批（会话记忆/凑单/代下单）**：① 会话记忆持久化（MemorySaver→本地 JSON 文件，跨进程/重启延续 + `DELETE /chat/session/{sid}` 清空 + BFF 透传 + H5 清空按钮）；② AI 凑单优化器（`optimize_cart`：购物车+SKU 现价/规格 + marketing 满减引擎 + 逐券试算最优）；③ 一键代下单（`create_order`：真实调 order-service 下单 + 用户确认流）。**实测**：SSE 全链路「怎么买最划算」→ 购物车 2 件 ¥148 明细+最优方案；跨轮「确认下单」→ 订单 2108152430193233920 真实落库（¥148）；会话清空接口 200。详见 CHANGELOG [0.2.4]。
* 2026-10-08：**CI 集成测试基建修复（全量 78 项测试全绿）**：① Testcontainers 加固——`@DynamicPropertySource` 注册标准键 `spring.datasource.url/username/password` + `spring.data.redis.host/port`（修 TEST_JDBC_URL 键名不被 Spring 识别）、MySQL/Redis 容器显式等待 + 120s 超时、payment/marketing/aftersale 补 Redis 容器、auth schema.sql 对齐生产迁移（user_id 列）；② test profile 安全链——9 服务 `SecurityConfig @Profile("!test")` 导致默认安全链全拒 401，新建 10 个 `TestSecurityConfig`（permitAll）接入测试类（inventory 非 Web 不导入）；③ 对齐测试与实现——cart mock product 校验 URL/响应、order 重复支付回调幂等断言改验状态机非法跃迁、OutboxDeliveryTask `@Profile("!test")`（test 无 MQ）、清理 payment 残留旧迁移（V20261007 → Flyway 重复 ADD 列）。**`mvn verify` 全模块 0 失败 0 错误（auth 3 / user 6 / file 6 / product 5 / cart 4 / order 10 / inventory 4 / payment 7 / marketing 8 / logistics 6 / aftersale 8 + MaskUtil 11）**，GitHub Actions CI `build-test` 可全绿。详见 CHANGELOG [0.2.3]。
* 2026-10-08：**P1 容量调优（写链路瓶颈消除）**：HikariCP 连接池全服务化（10 个 DB 服务统一 pool 50，pool-name ZhigouHikariPool）；复测③ 2000VU **错误率 0.00%**（0/86245）、RPS 303（+50%）、P95 5.02s（-36%，持续收敛 10.23→8.14→7.86→5.02）、5 类接口 checks 全 100%；P95 未达 1.5s 为单机写事务耗时，生产化路径（BFF 横向扩展/写链路异步化/分库分表）见报告 §6.3。详见 CHANGELOG [0.2.2]。


* 2026-10-08：**P0-C 售后逆向全流程闭环（退款真实资金流 + 退回库存联动）**：① payment-service 新增退款表 `payment_refund`（V20261092，refund_no 幂等键）+ `POST /payment/refund`（校验支付单 SUCCESS、金额 0<amount≤实付、沙箱即时 SUCCESS）；② aftersale-service 退款改真实链路（V20261093 加 sku_id/count/refund_no；refund：SELLER_APPROVED→REFUNDING→调 payment→REFUNDED 记 refundNo→调 inventory 回库存，失败告警"待人工补偿"；REFUNDED 幂等）；③ inventory-service 放行 preDeduct/confirm/rollback（此前 HTTP 通道 403，回库存一直靠 MQ 兜底）；④ BFF 新增 aftersale 透传模块（apply/mine/detail/cancel 注入 X-User-Id 头，修复 mine 恒 0 条）。**实测闭环**：¥1 单 apply→approve→refund→REFUNDED（RFF2452BD37CD04379）→payment_refund 落库→**库存自动回滚 99→100**；PENDING 单退款被正确拒绝；H5 售后 tab 7 条 + 详情页状态卡/退款单号展示正确。详见 CHANGELOG [0.1.5]。



* 2026-10-08（P1 三批）：**AI 决策辅助第三批——隐性需求挖掘 + 提示词对齐**——ai-orchestrator 新增 `analyze_user_context` 工具（历史订单→常购品类/价位带/复购倾向，无历史不编造），system.md 补齐工具清单与「拆解→隐性补全→搜索→比价→避坑→推荐」六步流程，红线修正为「禁止自主下单（用户确认后允许代下单）」；SSE 实测全链路通过（22 单历史 → 运动鞋/百元内）。
* 2026-10-07：**P0-B 交易最终一致性落地（outbox + RocketMQ）**：① order-service OutboxDeliveryTask（30s 周期投递 status=0 → MQ → 置 1，at-least-once）；② ORDER_CLOSED 事件体升级（含 items 明细），取消/超时关单统一新格式；③ inventory-service OrderClosedListener 消费兜底 + 
ollbackOrder(orderId, items) Redis SETNX 幂等（双通道只释放一次）；④ **RocketMQ broker 地址修复**（Windows Docker Desktop：容器内网 IP 宿主不可达 → -c 强制读取 conf/broker.conf + docker cp 覆盖 rokerIP1=127.0.0.1 + restart，clusterList 已显示 127.0.0.1:10911）。实测：投递 41/41 成功（status 全=1）；同 orderId 双消息幂等闭环——首条释放库存 94→96，次条跳过，只释放一次。详见 CHANGELOG [0.1.4]。\n* 2026-10-07：**P0-A 交易完整性第一梯队落地**：① 超时关单（order-service `OrderTimeoutTask` 每 5 分钟 + 启动首扫，INIT 超 15 分钟→CLOSED + outbox + 释放库存，首扫关闭 16 笔）；② 支付对账补偿（payment-service `notify_status` 迁移 V20261091 + reconcile 补偿 SUCCESS 未通知单 + 运维端点 `/payment/reconcile`，实测补偿 4 笔）。详见 CHANGELOG [0.1.3]。

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

* [x] ~~日志脱敏（AOP）~~（P0-D1 完成：packages/common mask 模块 + auth-center 接入，登录日志 phone/验证码/token 全掩、userId 不误掩，见 CHANGELOG [0.1.6]）

* [x] ~~Dockerfile（9 个缺失服务）+ 镜像构建~~（P0-D2 完成：11 服务生产级多阶段 Dockerfile + 根 .dockerignore + 11 个运行时镜像 zhigou/<svc>:0.1.0 + 生产编排 infra/compose/services.yml，见 CHANGELOG [0.1.7]；本地容器内 mvn 网络受限，多阶段 Stage1 留 CI 验证）

* [x] ~~CI/CD（build→test→镜像→部署）~~（P0-D3 完成：\.github/workflows/ci.yml\ build-test→build-images(GHCR)→summary + \.github/workflows/deploy.yml\ 手动部署，见 CHANGELOG [0.1.8]）

* [x] ~~全链路压测（k6 阶梯 100→500→1000→2000）~~（P0-D4 完成：k6 全链路脚本 + 基线 0 错误 + 全量压测定位瓶颈，见 CHANGELOG [0.1.9]；**P1 优化已落地并复测**（缓存/连接池/RAG 快速失败，QUICK 50VU 达标，见 CHANGELOG [0.2.0]））

### P1・AI 决策辅助（M2 与产品目标的差距核心）
- [x] 需求拆解（analyze_requirement：预算/品类/场景/偏好结构化，已上 SSE 链路）
- [x] 跨平台比价（compare_prices：多 SKU 价格/规格/库存横向对比）
- [x] 差评/避坑（review_analysis：选购提醒 + 评价数据待接入降级）
- [ ] 真伪测评/水军识别（依赖评价数据，待评价模块）
- [ ] 个性化推荐升级（推荐引擎，待画像数据积累）
- [ ] 图片搜款/多模态（待立项）
- [x] ~~AI 凑单优化器（券+满减最优组合）~~（[0.2.4] optimize_cart 工具交付：购物车+SKU 现价+满减引擎+逐券试算）
- [x] ~~一键代下单（Agent 对接下单 + 用户确认流）~~（[0.2.4] create_order 工具交付：真实下单 + 用户确认流实测）
- [x] ~~会话记忆持久化（当前 MemorySaver 进程内存）~~（[0.2.4] 本地文件持久化 + DELETE 清空接口 + H5 清空按钮；生产注释换 Redis）




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
* 2026-10-10：**P2 智能衣橱 + 家居管理（[0.2.15]）**：新建 closet-service（8093）衣物单品/穿搭方案/家居盘点三表 + 穿搭推荐规则引擎（场合+穿着次数轮换，note 标注演示）+ 补货清单（数量≤1 或 7 天内到期）；BFF +8 路由；H5 衣橱页整体改造（衣物/穿搭/家居三 tab）。验证：直连 11/11、BFF 9/9、H5 构建 + 浏览器实测。

* 2026-10-10：**P2 本地生活 MVP（[0.2.14]）**：新建 life-service（8092）POI 门店/商圈 + 服务 SKU + 到店预约（状态机 待确认/已确认/已完成/已取消），Seed 成都真实商圈演示数据；BFF +6 路由；H5 本地生活页（6 分类 tab）+ 门店详情（预约表单）+ 我的预约；首页宫格第 8 格。验证：直连 10/10、BFF 8/8、H5 构建 + 浏览器渲染。

* 2026-10-10：**P2 内容社区二期（[0.2.13]）**：短视频（图文 MVP，video_url 兼容真实视频 URL 即插即用）+ 直播（状态机预告/直播中/已结束，直播流演示占位标注）。community-service V2 迁移 2 表 + seed；BFF +7 路由；H5 短视频竖屏 feed 页 + 直播列表/详情页 + 首页宫格 2 入口 + 社区 tab。验证：直连 8/8、BFF 接口全通、H5 构建通过、浏览器渲染验证（短视频 5 条/直播 2 场）。

* 2026-10-10（注）：**git push 待重试**——[0.2.12] 与 [0.2.11 防编造补充] 两个 commit 已在本地（origin/main 落后 2），push 多次因 GitHub 443/Connection reset 失败，网络恢复后重试 `git push origin main`。

* 2026-10-10：**P2 内容社区 MVP（[0.2.12]）**：新增 community-service（8091，笔记/评论/点赞收藏/虚假内容识别 SPAM_WORDS+重复检测）；AI 种草文案 writer（真实数据锚点）；BFF 透传 8 路由 + 雪花 ID 字符串化修复 + ai-writer 超时豁免；H5 社区页换真（信息流/发布/AI 写文案/详情评论/营销标记）。验证：直连 11/11、BFF 全链路 7/7、双端构建通过；浏览器 UI 点按实测受 bu 会话 viewport 异常未完成（页面渲染文本验证通过，交互以 API 验收为准）。





* 2026-10-07：支付待付款终极根因修复（Security permitAll + BFF 不吞错 + 订单自动联动 PAID + 详情页轮询 + 订单列表筛选修复 + 售后 tab）；浏览器实测支付闭环"商家备货中"；悬浮购物车（414px 容器定位）；真实支付接入指南；全链路联调冒烟 17/17（12 服务 + BFF + H5 全启动，黄金路径全绿，6 处联调 bug 修复）；前端 8 屏对齐 + 交易闭环交付；BFF 六模块透传；order/cart 补口与 bug 修复；文档体系统一（docs/README 索引 + README/CLAUDE/progress 对齐）。

* 2026-10-02：BFF 压测；生产就绪度评估；Mock 数据机制。

* 2026-09-30：项目初始化。