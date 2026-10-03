<p align="center">
  <br/>
  <img src="docs/logo.png" width="120" alt="智购" />
  <h1 align="center">智购 · AI 原生超级商城</h1>
  <p align="center">
    AI 驱动的全渠道电商平台 · 12 微服务 · 对话式购物体验
  </p>
  <p align="center">
    <img src="https://img.shields.io/badge/Java-21-%23ED8B00?logo=openjdk" alt="Java 21" />
    <img src="https://img.shields.io/badge/Spring%20Boot-3.5.x-%236DB33F?logo=springboot" alt="Spring Boot 3.5" />
    <img src="https://img.shields.io/badge/Vue-3-%234FC08D?logo=vue.js" alt="Vue 3" />
    <img src="https://img.shields.io/badge/Python-3.11-%233776AB?logo=python" alt="Python 3.11" />
    <img src="https://img.shields.io/badge/NestJS-12-%23E0234E?logo=nestjs" alt="NestJS 12" />
    <img src="https://img.shields.io/badge/MySQL-8.0-%234479A1?logo=mysql" alt="MySQL 8.0" />
    <img src="https://img.shields.io/badge/Redis-7-%23DC382D?logo=redis" alt="Redis 7" />
    <img src="https://img.shields.io/badge/PostgreSQL-16-%234169E1?logo=postgresql" alt="PostgreSQL 16" />
  </p>


---

## 项目简介

**智购（ZhiGou）** 是一个 AI 原生超级商城 —— 用户可以用自然语言找商品、比价格、领优惠券，AI 导购「小智」全程陪伴。

> 「搜索框 → 列表 → 详情 → 加购 → 下单」的传统电商模式 → 「你说需求 → AI 理解 → AI 推荐 → 一键下单」的对话式购物体验

### 核心特性

- 🤖 **AI 导购** —— 基于 LangChain + LangGraph 的对话式购物助手，SSE 流式回复，支持工具调用（搜索、查价、查库存、推荐优惠券）
- 🛍️ **完整电商闭环** —— 商品管理 → 购物车 → 下单（幂等）→ 支付（沙箱）→ 库存扣减 → 物流 → 售后逆向流程
- 🔍 **语义搜索** —— PostgreSQL pgvector 实现商品语义检索，告别 MySQL `LIKE %keyword%`
- 📊 **可观测** —— Prometheus + Grafana + Loki 全栈监控，RED 指标 + JVM + 业务大盘
- 📱 **多端覆盖** —— 移动端 H5（Vue 3）+ 商家后台（Element Plus）+ BFF 聚合层（NestJS）
- 🏗️ **企业级工程化** —— Monorepo + 12 微服务 + JWT 鉴权 + Flyway 迁移 + Testcontainers 测试

## 快速开始

### 前置条件

- Java 21+（JDK 21）
- Node.js 20+、npm
- Python 3.11+
- Docker & Docker Compose
- Maven 3.9+

### 1. 启动中间件

```bash
docker compose -f infra/compose/middleware.yml up -d
```

一键启动：MySQL 8.0、Redis 7、RocketMQ 5.x、MinIO、PostgreSQL 16 + pgvector。

### 2. 启动后端服务

```bash
# 安装公共库
mvn install -pl packages/common -DskipTests

# 启动所有 Java 微服务（分终端启动）
##dev
$root = "D:\aafpc\Java\demo\zhigou\zhigou" $services = @( 'auth-center' , 'product-service' , 'cart-service' , 'order-service' , 'inventory-service' , 'payment-service' , 'marketing-service' , 'logistics-service' , 'aftersale-service' , 'user-service' , 'file-service' ) foreach ( $svc in $services ) {
  Write-Host "Starting $svc (dev) ..." -ForegroundColor Cyan
  Start-Process -FilePath "cmd.exe" `
    -ArgumentList "/k" , "mvn spring-boot:run -pl services/ $svc -Dspring-boot.run.profiles=dev" `
    -WorkingDirectory $root Start-Sleep -Seconds 2 }

##直接跑打包好的 jar（推荐，启动快，不用每次编译）
$root = "D:\aafpc\Java\demo\zhigou\zhigou"
$services = @{
  'auth-center'='zhigou-auth-center'; 'product-service'='zhigou-product-service'
  'cart-service'='zhigou-cart-service'; 'order-service'='zhigou-order-service'
  'inventory-service'='zhigou-inventory-service'; 'payment-service'='zhigou-payment-service'
  'marketing-service'='zhigou-marketing-service'; 'logistics-service'='zhigou-logistics-service'
  'aftersale-service'='zhigou-aftersale-service'; 'user-service'='zhigou-user-service'
  'file-service'='zhigou-file-service'
}

foreach ($svc in $services.Keys) {
  $jar = "services\$svc\target\$($services[$svc])-0.1.0-SNAPSHOT.jar"
  Write-Host "Starting $svc (dev) ..." -ForegroundColor Cyan
  Start-Process -FilePath "cmd.exe" `
    -ArgumentList "/k","java -jar $jar --spring.profiles.active=dev" `
    -WorkingDirectory $root
  Start-Sleep -Seconds 2
}
    
##上线
$root = "D:\aafpc\Java\demo\zhigou\zhigou"
$services = @(
  'auth-center','product-service','cart-service','order-service',
  'inventory-service','payment-service','marketing-service',
  'logistics-service','aftersale-service','user-service','file-service'
)

foreach ($svc in $services) {
  Write-Host "Starting $svc ..." -ForegroundColor Cyan
  Start-Process -FilePath "cmd.exe" `
    -ArgumentList "/k","mvn spring-boot:run -pl services/$svc" `
    -WorkingDirectory $root
  Start-Sleep -Seconds 2   # 错开启动，避免同时抢 CPU/内存
}
```

| 服务 | 端口 | 说明 |
|------|------|------|
| auth-center | 8080 | 认证中心（JWT 签发） |
| user-service | 8081 | 用户/地址管理 |
| file-service | 8082 | 文件上传（MinIO） |
| product-service | 8083 | 商品 SPU/SKU CRUD |
| cart-service | 8084 | 购物车 |
| order-service | 8085 | 订单管理（含状态机） |
| inventory-service | 8086 | 库存扣减（Redis Lua） |
| payment-service | 8087 | 沙箱支付 + T+1 对账 |
| marketing-service | 8088 | 优惠券/折扣引擎 |
| logistics-service | 8089 | 运费计算 + 物流轨迹 |
| aftersale-service | 8090 | 售后/逆向流程（状态机） |

### 3. 启动 AI 服务

```bash
pip install -r services/ai-orchestrator/requirements.txt
cd services/ai-orchestrator && python main.py
```

### 4. 启动 BFF 和前端

```bash
# BFF 聚合层（NestJS）
cd apps/bff-shop && npm install && npm run start:dev

# 移动端 H5（Vue 3）
cd apps/h5-shop && npm install && npm run dev

# 商家后台（Element Plus）
cd apps/admin-merchant && npm install && npm run dev
```

### 5. 验证

```bash
# 健康检查
curl http://localhost:8080/actuator/health   # auth-center

# 用户注册 + 登录
curl -X POST http://localhost:3000/auth/send-sms-code \
  -H "Content-Type: application/json" \
  -d '{"phone":"13800138000"}'
# 从 Redis 取验证码
CODE=$(docker exec zhigou-redis redis-cli GET "auth:sms:13800138000")
curl -X POST http://localhost:3000/auth/login \
  -H "Content-Type: application/json" \
  -d "{\"phone\":\"13800138000\",\"code\":\"$CODE\"}"

# 浏览首页
curl -H "Authorization: Bearer $TOKEN" http://localhost:3000/home/feed

# AI 对话
curl -N -X POST http://localhost:8000/api/v1/chat/sse \
  -H "Content-Type: application/json" \
  -d '{"query":"推荐跑步鞋","userId":"u1001","sessionId":"s001"}'
```

## 架构

```
                    ┌──────────────────────────────────────────┐
                    │          客户端 (H5 / Admin)              │
                    └──────────────┬───────────────────────────┘
                                   │ HTTP / SSE
                    ┌──────────────▼───────────────────────────┐
                    │     BFF 聚合层 (NestJS, port 3000)       │
                    │  JWT 验证 · 并行聚合 · SSE 透传 · 埋点    │
                    └────┬─────┬─────┬─────┬─────┬────────────┘
                         │     │     │     │     │
          ┌──────────────┘     │     │     │     └──────────────┐
          ▼                    ▼     ▼     ▼                    ▼
   ┌──────────┐   ┌────────┐ ┌──────┐ ┌────┐ ┌──────────┐ ┌─────────┐
   │auth-center│   │product │ │order │ │cart│ │inventory │ │payment  │
   │  8080    │   │ 8083   │ │8085  │ │8084│ │ 8086     │ │ 8087    │
   └──────────┘   └────────┘ └──────┘ └────┘ └──────────┘ └─────────┘
                                                                   
   ┌──────────┐   ┌────────┐ ┌──────┐ ┌────┐ ┌──────────┐ ┌─────────┐
   │marketing │   │logistics│ │after │ │user│ │   file   │ │   AI    │
   │  8088    │   │  8089   │ │8090  │ │8081│ │   8082   │ │  8000   │
   └──────────┘   └────────┘ └──────┘ └────┘ └──────────┘ └─────────┘
                                                                   
   ┌──────────────────────────────────────────────────────────────┐
   │                   基础设施层                                  │
   │  MySQL · Redis · RocketMQ · MinIO · PostgreSQL + pgvector    │
   └──────────────────────────────────────────────────────────────┘
   ┌──────────────────────────────────────────────────────────────┐
   │                   可观测性                                    │
   │  Prometheus :9090 · Grafana :3001 · Loki :3100               │
   └──────────────────────────────────────────────────────────────┘
```

### 数据流

```
                 ┌───────────────────┐
                 │  用户输入需求       │
                 │  "推荐跑步鞋"      │
                 └────────┬──────────┘
                          ▼
               ┌─────────────────────┐
               │  AI Orchestrator     │
               │  (LangGraph Agent)   │
               │                      │
               │  1. search_products  │──▶ product-service
               │  2. get_price        │──▶ product-service
               │  3. check_inventory  │──▶ inventory-service
               │  4. get_user_profile │──▶ user-service
               │  5. apply_coupon     │──▶ marketing-service
               └─────────────────────┘
                          │
                          ▼
               ┌─────────────────────┐
               │  SSE 流式回复        │
               │  token → 卡片 → done │
               └─────────────────────┘
```

## 技术栈

### 后端

| 类别 | 技术 | 用途 |
|------|------|------|
| 语言 | Java 21 | 微服务 |
| 框架 | Spring Boot 3.5.x + Spring Cloud Alibaba 2023.x | 服务框架 |
| ORM | MyBatis-Plus 3.5.x | 数据库访问 |
| 数据库 | MySQL 8.0 · PostgreSQL 16 + pgvector | 业务 + 向量检索 |
| 缓存 | Redis 7 | 缓存/分布式锁/幂等 |
| 消息 | RocketMQ 5.x | 异步/最终一致性 |
| 鉴权 | JWT (HS256) + Nimbus JOSE | 认证 |
| AI | Python 3.11 + FastAPI + LangChain 1.x | 对话/Agent |
| 监控 | Prometheus + Grafana + Loki + OTel | 可观测 |

### 前端

| 类别 | 技术 |
|------|------|
| 移动端 H5 | Vue 3 + Vite + TypeScript + Pinia |
| 商家后台 | Vue 3 + Element Plus |
| BFF | NestJS 12 + TypeScript |
| SSE | fetch + ReadableStream (POST) |

### DevOps

| 类别 | 工具 |
|------|------|
| 编排 | Docker Compose |
| 部署 | K8s (Helm chart 开发中) |
| CI | (待接入) |
| 迁移 | Flyway |

## 项目结构

```
zhigou/
├── services/                    # Java 微服务 (11个)
│   ├── auth-center/             #   认证中心
│   ├── user-service/            #   用户服务
│   ├── product-service/         #   商品服务
│   ├── cart-service/            #   购物车
│   ├── order-service/           #   订单
│   ├── inventory-service/       #   库存
│   ├── payment-service/         #   支付
│   ├── marketing-service/       #   营销
│   ├── logistics-service/       #   物流
│   ├── aftersale-service/       #   售后
│   └── file-service/            #   文件
├── ai-services/                 # AI 服务
│   └── ai-orchestrator/         #   AI 导购 (Python FastAPI)
├── apps/                        # 前端应用
│   ├── bff-shop/                #   BFF 聚合层 (NestJS)
│   ├── h5-shop/                 #   移动端 H5 (Vue 3)
│   └── admin-merchant/          #   商家后台 (Element Plus)
├── packages/                    # 公共库
│   ├── common/                  #   Java 公共库 (Result/异常/JWT)
│   └── proto/                   #   契约定义
├── infra/                       # 基础设施
│   ├── compose/                 #   Docker Compose (中间件+监控)
│   └── helm/                    #   K8s Charts (WIP)
├── scripts/                     # 工具脚本
│   ├── e2e-order.sh             #   端到端冒烟
│   ├── backfill_vec.py          #   向量全量回填
│   └── load-test/               #   压测 (k6)
├── config/                      # 运行时配置
│   └── fallback.yml             #   AI 降级开关
├── conf/                        # 基础设施配置
│   ├── prometheus/              #   Prometheus 抓取配置
│   ├── grafana/                 #   Grafana 数据源+面板
│   └── loki/                    #   Loki 存储配置
├── prompts/                     # AI 系统提示词
│   └── system.md                #   导购「小智」角色设定
├── docs/                        # 文档
│   ├── adr/                     #   架构决策记录
│   ├── progress.md              #   项目进度
│   ├── load-test/               #   压测报告
│   └── release-checklist.md     #   发布检查清单
└── CLAUDE.md                    # Claude Code 项目指令
```

## 端到端流程

```
用户发验证码 ─▶ 登录拿 JWT ─▶ 浏览首页 ─▶ AI 对话
                                  │
                                  ▼
                             商品详情 ─▶ 加购
                                  │
                                  ▼
                 ┌─── 下单(requestId 幂等) ───┐
                 │       │                    │
                 │       ▼                    │
                 │   沙箱支付                  │
                 │       │                    │
                 │  回调确认状态=PAID          │
                 │       │                    │
                 │   库存扣减                  │
                 │       │                    │
                 │   物流单生成                │
                 │       │                    │
                 │   ┌───┘                    │
                 │   ▼                        │
                 │ 售后申请                    │
                 │    │                       │
                 │  审核同意                   │
                 └───────────────────────────┘
```

## 测试

```bash
# Java 服务测试
mvn test -pl services/auth-center,services/product-service,services/order-service

# Python 测试
cd services/ai-orchestrator && pytest

# 端到端冒烟
bash scripts/e2e-order.sh

# 压测
k6 run scripts/load-test/smoke-test.js
```

## 压测结果（BFF 层）

| 并发 | 总请求 | 错误率 | P50 | P95 | P99 | RPS |
|------|--------|--------|-----|-----|-----|-----|
| 5 | 150 | 0% | — | 40ms | — | — |
| 20 | 1,500 | 0% | 46ms | 102ms | 504ms | 387 |
| 50 | 4,500 | 0% | 77ms | 131ms | 142ms | 718 |

> 完整报告见 [docs/load-test/](docs/load-test/)

## 项目状态

> **当前阶段**: M0 脚手架 / M1 交易闭环（进行中）
> [docs/progress.md](docs/progress.md)

| 里程碑 | 状态 |
|--------|------|
| M0 脚手架 — Monorepo + 中间件 + auth-center 登录 | ✅ |
| M1 交易闭环 — 下单→支付→库存→物流 | 🚧 |
| M2 AI 导购 — SSE + RAG + 降级 | ✅ |
| M3 大促压测 — 5000 QPS | ⬜ |
| M4 上线 — 灰度 + 监控 + 回滚 | ⬜ |

## 贡献指南

1. Fork 本仓库
2. 从 `develop` 创建 feature 分支：`feature/<服务名>-<描述>`
3. 提交遵循 [Conventional Commits](https://www.conventionalcommits.org/)
4. 确保 `mvn test` 和 `pytest` 通过
5. 创建 PR 合入 `develop`

### 提交规范

```
feat(order): 新增创建订单接口，支持 requestId 幂等
fix(payment): 修复沙箱支付回调重复消费问题
refactor(inventory): 把 Lua 脚本抽到独立类
test(cart): 补充购物车并发加购测试
docs: 更新 ADR-0002
```

## 红线

1. ❌ 不允许跨服务直连对方数据库
2. ❌ 不允许把密钥写入代码
3. ❌ 不允许跳过测试
4. ❌ 不允许暴露栈信息给前端
5. ❌ 不允许生产环境开 DEBUG 日志

## 许可证

[MIT](LICENSE)

---

<p align="center">
  <sub>Built with ❤️ by the 智购 Team</sub>
</p>