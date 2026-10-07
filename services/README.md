# services · 微服务总览

> 智购（**AI 原生超级购物生态 APP**，产品愿景见 `../docs/智购功能文档.md`）的微服务层。

共 **12 个服务**：11 个 Java（Spring Boot 3.5）+ 1 个 Python（FastAPI AI 导购）。端口分配、职责与关键能力如下。

## 服务清单

| 服务 | 端口 | 数据库 | 职责 | 关键能力 |
|---|---|---|---|---|
| auth-center | 8080 | zhigou_auth | 认证中心 | 手机号验证码登录、JWT 签发（HS256） |
| user-service | 8081 | zhigou_user | 用户域 | 资料/地址 CRUD、默认地址、手机号 AES 加密 |
| file-service | 8082 | zhigou_file | 文件域 | MinIO 上传、文件元数据 |
| product-service | 8083 | zhigou_product | 商品域 | SPU/SKU CRUD、分页查询、SKU 校验 |
| cart-service | 8084 | Redis（`cart:{userId}`） | 购物车 | 加购/更新/删除单条/清空/查询（无 DataSource） |
| order-service | 8085 | zhigou_order | 订单域 | 下单（requestId 幂等 + 真实价格）、状态机、取消、`GET /order/mine` |
| inventory-service | 8086 | zhigou_inventory | 库存域 | Redis Lua 原子扣减（防超卖） |
| payment-service | 8087 | zhigou_payment | 支付域 | 支付单创建、沙箱 mock 支付、T+1 对账骨架 |
| marketing-service | 8088 | zhigou_marketing | 营销域 | 优惠券、折扣计算 |
| logistics-service | 8089 | zhigou_logistics | 履约域 | 运费计算、物流轨迹 |
| aftersale-service | 8090 | zhigou_aftersale | 售后域 | 逆向状态机（APPLYING→SELLER_APPROVED→REFUNDING→REFUNDED） |
| ai-orchestrator | 8000 | PostgreSQL + pgvector | AI 导购 | LangChain/LangGraph Agent、SSE、RAG、5 个工具函数、降级 |

## 通用约定

- **统一返回**：`Result<T> { code, message, data }`（来自 `packages/common`）
- **鉴权**：BFF 解析 JWT 后透传 `x-user-id` header；服务优先读 `x-user-id`（order/cart 已实现，其余按 P0 补 JWT 过滤器）
- **金额**：`Long` 存分；Snowflake ID 序列化为字符串（避免 JS 精度丢失）
- **数据**：Flyway 迁移（`db/migration`）+ dev profile mock（`db/mock`）；所有表含 `id/create_time/update_time/version/deleted`
- **配置**：`application-{env}.yml` + 环境变量（密钥不进代码）

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

> Mock 数据机制与清除见 [../docs/mock-data-cleanup-guide.md](../docs/mock-data-cleanup-guide.md)；AI 服务独立说明见 `services/ai-orchestrator/`（prompts/ 下为系统提示词与 few-shot 模板）。
