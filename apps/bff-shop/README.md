# bff-shop · BFF 聚合层

智购移动端的 BFF（Backend For Frontend）聚合层，基于 **NestJS 12 + TypeScript**，端口 **3000**。

## 职责

- **JWT 鉴权**：`JwtAuthGuard` 解析请求头 Bearer token，向 `req.userId` 注入用户身份；下游调用统一透传 `x-user-id` header
- **并行聚合**：对客户端屏蔽服务间调用，`Promise.all` 并行聚合多个下游
- **SSE 透传**：AI 对话流式接口原样透传 `ai-orchestrator`
- **降级兜底**：下游服务失败时 `catchError` 降级返回空数据，不阻断主流程
- **统一响应**：所有接口返回 `ApiResponse.ok()` / `ApiResponse.fail()` 结构

## 模块与接口

| 模块 | 接口 | 说明 |
|---|---|---|
| `home` | `GET /home/feed` | 首页 feed（并行聚合商品/推荐） |
| `product` | `GET /product/page`、`GET /product/:id/detail`、`GET /product/sku/:skuId`、`GET /product/sku/:skuId/validate` | 商品透传（page 为分页列表） |
| `chat` | `POST /chat/sse` | AI 对话 SSE 透传 |
| `auth` | `POST /auth/send-sms-code`、`POST /auth/login`、`POST /auth/refresh` | 认证透传 |
| `cart` | `GET /cart/mine`、`POST /cart/add`、`PUT /cart/update`、`DELETE /cart/:skuId`、`POST /cart/clear` | 购物车聚合（SKU/SPU 真实信息） |
| `order` | `POST /order/create`、`GET /order/mine`、`GET /order/:orderId`、`POST /order/:orderId/cancel` | 订单透传 |
| `payment` | `POST /payment/create`、`POST /payment/sandbox/mock-pay` | 沙箱支付透传 |
| `marketing` | `GET /coupon/mine`、`POST /discount/calculate` 等 | 营销透传 |
| `user` | `GET /user/profile`、`PUT /user/profile`、地址 CRUD | 用户资料 + 地址聚合 |
| `logistics` | `POST /freight/calculate` 等 | 物流透传 |

> 完整接口清单与页面数据来源见 [../../docs/前端改造说明-原型对齐与后端对接.md](../../docs/前端改造说明-原型对齐与后端对接.md)。

## 启动与验证

```bash
npm install
npm run start:dev        # 开发（:3000）
npm run build            # 构建验证（nest build）
```

## 依赖

- 上游：11 个 Java 微服务（地址见 `src/config/service.config.ts`）
- 鉴权：`x-user-id` header 由 JwtAuthGuard 注入
