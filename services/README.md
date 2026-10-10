# services · 微服务总览

> 智购（**AI 原生超级购物生态 APP**，产品愿景见 `../docs/智购功能文档.md`，架构蓝图见 `../docs/智购-企业级工程化技术方案.md`）的微服务层。

共 **16 个服务**：15 个 Java（Spring Boot 3.5）+ 1 个 Python（FastAPI AI 编排）。端口分配、职责与关键能力如下。

## 服务清单

| 服务 | 端口 | 数据库 | 职责 | 关键能力 |
|---|---|---|---|---|
| auth-center | 8080 | zhigou_auth | 认证中心 | 手机号验证码登录、JWT 签发（HS256）、日志脱敏 |
| user-service | 8081 | zhigou_user | 用户域 | 资料/地址 CRUD、默认地址、手机号 AES 加密 |
| file-service | 8082 | zhigou_file | 文件域 | MinIO 上传、文件元数据 |
| product-service | 8083 | zhigou_product | 商品域 | SPU/SKU CRUD、分页查询、语义检索、跨平台比价、销量预测/智能选品/动态定价（商家端） |
| cart-service | 8084 | Redis（`cart:{userId}`） | 购物车 | 加购/更新/删除单条/清空/查询（无 DataSource） |
| order-service | 8085 | zhigou_order | 订单域 | 下单（requestId 幂等 + 真实价格）、状态机、取消、经营统计、履约预警/异常处理 |
| inventory-service | 8086 | zhigou_inventory | 库存域 | Redis Lua 原子扣减（防超卖）、低库存预警、自动补货 |
| payment-service | 8087 | zhigou_payment | 支付域 | 支付单创建、沙箱 mock 支付、T+1 对账、退款资金流 |
| marketing-service | 8088 | zhigou_marketing | 营销域 | 优惠券、折扣计算、拼团、营销方案 |
| logistics-service | 8089 | zhigou_logistics | 履约域 | 运费计算、物流轨迹、延误预警/一键调度 |
| aftersale-service | 8090 | zhigou_aftersale | 售后域 | 逆向状态机（APPLYING→SELLER_APPROVED→REFUNDING→REFUNDED）、质保提醒/维修预约 |
| community-service | 8091 | zhigou_community | 内容域 | 笔记/评论/点赞收藏、短视频/直播、虚假内容识别 |
| life-service | 8092 | zhigou_life | 本地生活 | POI/商圈/服务 SKU、到店预约状态机 |
| closet-service | 8093 | zhigou_closet | 衣橱家居 | 衣物/穿搭/家居盘点、穿搭推荐、补货清单 |
| wallet-service | 8094 | zhigou_wallet | 钱包/会员 | 余额/流水（bizNo 幂等）、沙箱充值、会员等级、AI 订阅 |
| ai-orchestrator | 8095 | PostgreSQL + pgvector | AI 编排 | FastAPI + LangChain/LangGraph Agent、SSE 流式回复、RAG、29 个 AI 工具、降级兜底 |

## 通用约定

- **统一返回**：`Result<T> { code, message, data }`（来自 `packages/common`）
- **鉴权**：BFF 解析 JWT 后透传 `x-user-id` header；服务优先读 `x-user-id`；AI 直连端点需在 `SecurityConfig` 显式放行
- **金额**：`Long` 存分；Snowflake ID 序列化为字符串（避免 JS 精度丢失）
- **数据**：Flyway 迁移（`db/migration`）+ dev profile mock（`db/mock`）；所有表含 `id/create_time/update_time/version/deleted`
- **配置**：`application-{env}.yml` + 环境变量（密钥不进代码）
- **AI 工具**：`ai-orchestrator/tools.py` 的 TOOLS 列表为唯一真相（29 个，含搜索/比价/避坑/凑单/代下单/送礼/拼团/售后/物流/商家经营等）；新工具必须同时入列表

## 启动

```bash
# 编译（先装公共库）
mvn install -pl packages/common -DskipTests
mvn -DskipTests package

# 单服务开发启动（带 mock）
mvn spring-boot:run -pl services/order-service -Dspring-boot.run.profiles=dev

# 或直接跑 jar（推荐，快）
java -jar services/order-service/target/zhigou-order-service-0.1.0-SNAPSHOT.jar --spring.profiles.active=dev
```

> Mock 数据机制与清除见 [../docs/mock-data-cleanup-guide.md](../docs/mock-data-cleanup-guide.md)；AI 服务独立说明见 `services/ai-orchestrator/`（prompts/ 下为系统提示词与 few-shot 模板）；一键启动 16 服务见根 `README.md`「快速开始」。
