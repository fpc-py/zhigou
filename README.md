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
</p>

---

## 项目简介

**智购（ZhiGou）** 是一个 **AI 原生超级购物生态 APP**——以个人智能购物助理「小智」为核心入口，重构「人-货-场」关系，实现从「人找货」到「AI 找人货匹配」的范式跃迁：用户用自然语言说需求，AI 理解需求、推荐商品、比价、辅助决策，全程可信高效。

> 当前已落地 **对话式购物（AI 导购）+ 完整交易闭环**；最终愿景聚合 **商品零售、服务消费、内容种草、社交分享、金融服务** 五大生态（产品愿景详见 [docs/智购功能文档.md](docs/智购功能文档.md)）。

### 核心特性

- 🤖 **AI 导购** —— 基于 LangChain + LangGraph 的对话式购物助手「小智」，SSE 流式回复，支持工具调用（搜索、查价、查库存、推荐优惠券），对话结果按关键词拉取真实商品卡
- 🛍️ **完整电商闭环** —— 商品 → 购物车（服务端）→ 下单（requestId 幂等 + 真实价格）→ 沙箱支付 → 库存扣减（Redis Lua）→ 物流 → 售后逆向流程
- 🔍 **语义搜索** —— PostgreSQL pgvector 实现商品语义检索，告别 MySQL `LIKE %keyword%`
- 📊 **可观测** —— Prometheus + Grafana + Loki 全栈监控，RED 指标 + JVM + 业务大盘
- 📱 **多端覆盖** —— 移动端 H5（Vue 3，8 屏对齐原型）+ 商家后台（Element Plus）+ BFF 聚合层（NestJS）
- 🏗️ **企业级工程化** —— Monorepo + 12 微服务 + JWT 鉴权 + Flyway 迁移 + Testcontainers 测试

### 项目状态

| 里程碑 | 状态 |
|--------|------|
| M0 脚手架 — Monorepo + 中间件 + auth-center 登录 | ✅ |
| M1 交易闭环 — 下单→支付→库存→物流 | ✅ 全链路联调 17/17 |
| M2 AI 导购 — SSE + RAG + 降级 + 对话商品卡 | ✅ |
| M3 大促压测 — 5000 QPS | ⬜ |
| M4 上线 — 灰度 + 监控 + 回滚 | ⬜ |

> 详细进度见 [docs/progress.md](docs/progress.md)；与目标态的差距与执行顺序见 [docs/智购开发任务差距分析报告.md](docs/智购开发任务差距分析报告.md)。

### 文档导航

**所有文档的统一索引见 [docs/README.md](docs/README.md)**。核心文档：

| 想了解 | 看这里 |
|--------|--------|
| 产品愿景（五大生态、AI 全链路） | [docs/智购功能文档.md](docs/智购功能文档.md) |
| 可交互原型（H5 已对齐） | [docs/智购AI超级商城-企业级可交互原型.html](docs/智购AI超级商城-企业级可交互原型.html) |
| 生产级架构蓝图 | [docs/智购-企业级工程化技术方案.md](docs/智购-企业级工程化技术方案.md) |
| 架构决策记录 | [docs/adr/](docs/adr/) |
| 前端改造与 BFF 接口清单 | [docs/前端改造说明-原型对齐与后端对接.md](docs/前端改造说明-原型对齐与后端对接.md) |
| 生产就绪度评估（已并入差距报告附录 A） | [docs/智购开发任务差距分析报告.md](docs/智购开发任务差距分析报告.md) |
| 发布检查清单 | [docs/release-checklist.md](docs/release-checklist.md) |

## 快速开始

### 前置条件

- Java 21+（JDK 21）、Maven 3.9+
- Node.js 20+、npm
- Python 3.11+
- Docker & Docker Compose

### 1. 启动中间件

```bash
docker compose -f infra/compose/middleware.yml up -d
```

一键启动：MySQL 8.0、Redis 7、RocketMQ 5.x、MinIO、PostgreSQL 16 + pgvector。

### 2. 编译并启动后端服务（Windows / PowerShell）

```powershell
# ① 安装公共库
mvn install -pl packages/common -DskipTests

# ② 编译全部服务（或对单个服务 mvn -pl services/<svc> compile）
mvn -DskipTests package

# ③ 开发模式启动（带 mock 数据，profile=dev）——推荐直接跑 jar，启动快
$root = "D:\aafpc\Java\demo\zhigou\zhigou"
$services = @(
  'auth-center','user-service','file-service','product-service','cart-service',
  'order-service','inventory-service','payment-service','marketing-service',
  'logistics-service','aftersale-service'
)
foreach ($svc in $services) {
  $jar = "services\$svc\target\zhigou-$svc-0.1.0-SNAPSHOT.jar"
  Start-Process -FilePath "cmd.exe" -ArgumentList "/k", "java -jar `"$jar`" --spring.profiles.active=dev" -WorkingDirectory $root
  Start-Sleep -Seconds 2   # 错开启动，避免同时抢 CPU/内存
}
```

> 中间件默认账号密码见 `infra/compose/middleware.yml`；Mock 数据机制与上线清除见 [docs/mock-data-cleanup-guide.md](docs/mock-data-cleanup-guide.md)。

### 服务端口对照

| 服务 | 端口 | 说明 |
|------|------|------|
| auth-center | 8080 | 认证中心（JWT 签发） |
| user-service | 8081 | 用户/地址管理 |
| file-service | 8082 | 文件上传（MinIO） |
| product-service | 8083 | 商品 SPU/SKU CRUD |
| cart-service | 8084 | 购物车（Redis） |
| order-service | 8085 | 订单管理（含状态机） |
| inventory-service | 8086 | 库存扣减（Redis Lua） |
| payment-service | 8087 | 沙箱支付 + T+1 对账 |
| marketing-service | 8088 | 优惠券/折扣引擎 |
| logistics-service | 8089 | 运费计算 + 物流轨迹 |
| aftersale-service | 8090 | 售后/逆向流程（状态机） |
| ai-orchestrator | 8000 | AI 导购（Python FastAPI） |

### 3. 启动 AI 服务

```bash
pip install -r services/ai-orchestrator/requirements.txt
cd services/ai-orchestrator && python main.py
```

### 4. 启动 BFF 和前端

```bash
# BFF 聚合层（NestJS, :3000）
cd apps/bff-shop && npm install && npm run start:dev

# 移动端 H5（Vue 3, :5173，已配 /api 代理到 BFF）
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

# AI 对话（SSE）
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

### AI 数据流

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
               │  token → 商品卡 → done│
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
| CI | (待接入，见差距报告附录 A P1-6) |
| 迁移 | Flyway |

## 项目结构

```
zhigou/
├── services/                    # 微服务 (11 Java + 1 Python)
│   ├── auth-center/             #   认证中心 :8080
│   ├── user-service/            #   用户服务 :8081
│   ├── file-service/            #   文件服务 :8082
│   ├── product-service/         #   商品服务 :8083
│   ├── cart-service/            #   购物车 :8084
│   ├── order-service/           #   订单 :8085
│   ├── inventory-service/       #   库存 :8086
│   ├── payment-service/         #   支付 :8087
│   ├── marketing-service/       #   营销 :8088
│   ├── logistics-service/       #   物流 :8089
│   ├── aftersale-service/       #   售后 :8090
│   └── ai-orchestrator/         #   AI 导购 (Python FastAPI) :8000
├── apps/                        # 前端应用
│   ├── bff-shop/                #   BFF 聚合层 (NestJS) :3000
│   ├── h5-shop/                 #   移动端 H5 (Vue 3) :5173
│   └── admin-merchant/          #   商家后台 (Element Plus)
├── packages/                    # 公共库
│   ├── common/                  #   Java 公共库 (Result/异常/JWT)
│   └── proto/                   #   契约定义
├── infra/                       # 基础设施
│   ├── compose/                 #   Docker Compose (中间件+监控)
│   └── helm/                    #   K8s Charts (WIP)
├── scripts/                     # 工具脚本
│   ├── smoke/                   #   全链路冒烟 (zhigou-e2e.ps1, 17 步)
│   ├── backfill_vec.py          #   向量全量回填
│   ├── load-test/               #   压测 (k6 / seed-data)
│   └── mock-data/               #   mock 清除脚本
├── config/                      # 运行时配置
│   └── fallback.yml             #   AI 降级开关
├── conf/                        # 基础设施配置
│   ├── prometheus/              #   Prometheus 抓取配置
│   ├── grafana/                 #   Grafana 数据源+面板
│   └── loki/                    #   Loki 存储配置
├── prompts/                     # AI 系统提示词
│   └── system.md                #   导购「小智」角色设定
├── docs/                        # 文档中心（索引见 docs/README.md）
└── CLAUDE.md                    # AI 协作项目指令
```

## 测试

```bash
# Java 服务测试
mvn test -pl services/auth-center,services/product-service,services/order-service

# Python 测试
cd services/ai-orchestrator && pytest

# 端到端冒烟（全链路 17 步黄金路径；前置：12 微服务 + BFF 3000 + H5 5173 全启动）
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/smoke/zhigou-e2e.ps1
# 期望输出：PASS=17 FAIL=0（最新验证 2026-10-07）

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

## 红线

1. ❌ 不允许跨服务直连对方数据库
2. ❌ 不允许把密钥写入代码
3. ❌ 不允许跳过测试
4. ❌ 不允许暴露栈信息给前端
5. ❌ 不允许生产环境开 DEBUG 日志

> 完整规范见 [CLAUDE.md](CLAUDE.md) 与 [CONTRIBUTING.md](CONTRIBUTING.md)；发布流程见 [docs/release-checklist.md](docs/release-checklist.md)。

## 许可证

MIT（LICENSE 文件待补充）

---

<p align="center">
  <sub>Built with ❤️ by the 智购 Team</sub>
</p>
