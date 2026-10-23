<p align="center">
  <img src="assets/logo.png" width="120" alt="智购" />
  <h1 align="center">智购 · AI 原生超级商城</h1>
  <p align="center">
    AI 驱动的全渠道电商平台 · 16 微服务 · 37 个 AI 工具 · 对话式购物体验
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

**智购（ZhiGou）** 是一个 **AI 原生超级购物生态平台**——以个人智能购物助理「小智」为核心入口，重构「人-货-场」关系：用户用自然语言说需求，AI 理解需求、推荐商品、比价、凑单、代下单，全程可信高效。

> 已落地：**对话式购物（AI 导购）+ 完整交易闭环 + 内容社区 + 智能衣橱 + 本地生活 + 商家经营 + 供应链**；从「人找货」到「AI 找人货匹配」。

### 核心特性

- 🤖 **AI 导购「小智」** —— SSE 流式对话，**37 个 AI 工具**（搜索/推荐/比价/差评避坑/凑单/代下单/送礼/拼团/售后/物流/补货/省钱/定时购/售后管家/客服/主动提醒/衣橱识别/每日穿搭/商家经营/经营大脑等），工具结果按关键词拉取真实商品卡
- 🛍️ **完整交易闭环** —— 商品 → 服务端购物车 → 下单（requestId 幂等 + 真实价格）→ 沙箱支付 → 库存扣减（Redis Lua）→ 物流 → 售后逆向流程（含部分退款/部分发货）
- 💰 **营销引擎** —— 优惠券/折扣叠加、秒杀活动、拼团、会员订阅
- 🧠 **AI 省钱助手** —— 跨平台比价、凑单优化、降价提醒 + 历史价格
- 📱 **内容社区** —— 种草笔记、短视频、直播（含预约/商品挂载）
- 👔 **智能衣橱** —— 拍照识别零录入 + 每日穿搭推荐（AI 视觉识别自动入库）
- 🏪 **本地生活** —— POI 到店、预约服务
- 📊 **商家中心** —— 经营统计 + AI 经营助手、预警、售后工作台、经营大脑（销量预测/动态定价/选品）
- 🔗 **供应链** —— 待发货/SKU 明细、履约预警、自动补货、异常订单自动处理
- 🔍 **语义搜索** —— PostgreSQL pgvector 商品语义检索
- 📈 **可观测** —— Prometheus + Grafana + Loki；AI 链路 metrics（工具调用/LLM/SSE 全埋点）
- 🏗️ **企业级工程化** —— Monorepo + 16 微服务 + JWT + Flyway + Testcontainers + GitHub Actions CI/CD（13 checks 绿）+ 限流熔断 + 19 位 id 精度全链路闭环

### 项目状态

| 里程碑 | 状态 |
|--------|------|
| M0 脚手架 — Monorepo + 中间件 + auth-center 登录 | ✅ |
| M1 交易闭环 — 下单→支付→库存→物流 | ✅ 全链路联调 17/17 |
| M2 AI 导购 — SSE + RAG + 降级 + 对话商品卡 | ✅ |
| M3 大促压测 — 5000 QPS 容量模型 | ✅ BFF 横扩 1.83x + 读层 1263 RPS + 单机可解释容量验收 |
| M4 上线 — 灰度 + 监控 + 回滚 | ✅ 回滚演练实测闭环 |

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
   │marketing │   │logistics│ │after │ │user│ │   file   │ │community│
   │  8088    │   │  8089   │ │8090  │ │8081│ │   8082   │ │  8091   │
   └──────────┘   └────────┘ └──────┘ └────┘ └──────────┘ └─────────┘

   ┌──────────┐   ┌────────┐ ┌──────┐ ┌────┐ ┌──────────────────────┐
   │   life    │   │ closet  │ │wallet│ │ AI │ │ 可观测              │
   │  8092    │   │  8093   │ │8094  │ │8095│ │ Prom :9090 · Graf :3001│
   └──────────┘   └────────┘ └──────┘ └────┘ └──────────────────────┘

   ┌──────────────────────────────────────────────────────────────┐
   │                   基础设施层                                  │
   │  MySQL · Redis · RocketMQ · MinIO · PostgreSQL + pgvector    │
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
               │  (原生 OpenAI 工具循环)   │
               │                      │
               │  1. search_products  │──▶ product-service
               │  2. get_price        │──▶ product-service
               │  3. check_inventory  │──▶ inventory-service
               │  4. get_user_profile │──▶ user-service
               │  5. apply_coupon     │──▶ marketing-service
               │  6-37. 比价/避坑/凑单/送礼/拼团/售后/物流/省钱/定时购/售后管家/客服/提醒/衣橱/穿搭/商家经营… │──▶ 各业务服务
               └─────────────────────┘
                          │
                          ▼
               ┌─────────────────────┐
               │  SSE 流式回复        │
               │  token → 商品卡 → done│
               └─────────────────────┘
```

## 快速开始（Quick Start）

### 前置条件

| 依赖 | 版本 | 说明 |
|------|------|------|
| JDK | 21+ | Java 微服务 |
| Maven | 3.9+ | 构建 |
| Node.js | 20+ | BFF / H5 / Admin |
| Python | 3.11+ | AI Orchestrator |
| Docker Desktop | WSL2 后端 | 一键启动中间件 |

> Windows 用户请确保 Docker Desktop 使用 WSL2 后端；RocketMQ 在 Windows Docker 下有一个已知坑，见下文第 1 步。

### 第 1 步：一键启动中间件

```bash
docker compose -f infra/compose/middleware.yml up -d
```

一键启动：**MySQL 8.0、Redis 7、RocketMQ 5.x、MinIO、PostgreSQL 16 + pgvector**（默认端口与账号见 `infra/compose/middleware.yml`；MySQL 映射 3307、MinIO 映射 9020/9021 以避免本机端口冲突）。

> **Windows 坑（必读）**：RocketMQ broker 默认向 namesrv 注册**容器内网 IP**，宿主机不可达会导致 Java 生产者 `sendDefaultImpl call timeout`。启动后执行一次：
> ```powershell
> powershell -ExecutionPolicy Bypass -File scripts/mq-fix-broker-ip.ps1
> ```
> （用 `broker.conf` 覆盖容器配置并重启 broker，`-c` 强制读取。）

### 第 2 步：编译并启动后端（15 个 Java 服务）

```bash
# ① 安装公共库
mvn install -pl packages/common -DskipTests

# ② 编译全部服务（或对单个服务 mvn -pl services/<svc> compile）
mvn -DskipTests package
```

开发模式启动（带 mock 数据，profile=dev）——Windows / PowerShell：

```powershell
$root = "<项目根目录>"
$services = @(
  'auth-center','user-service','file-service','product-service','cart-service',
  'order-service','inventory-service','payment-service','marketing-service',
  'logistics-service','aftersale-service','community-service','life-service',
  'closet-service','wallet-service'
)
foreach ($svc in $services) {
  $jar = "services\$svc\target\zhigou-$svc-0.1.0-SNAPSHOT.jar"
  Start-Process -FilePath "cmd.exe" -ArgumentList "/k", "java -jar `"$jar`" --spring.profiles.active=dev" -WorkingDirectory $root
  Start-Sleep -Seconds 2   # 错开启动，避免同时抢 CPU/内存
}
```

服务端口对照：

| 服务 | 端口 | 说明 |
|------|------|------|
| auth-center | 8080 | 认证中心（JWT 签发） |
| user-service | 8081 | 用户/地址管理 |
| file-service | 8082 | 文件上传（MinIO） |
| product-service | 8083 | 商品 SPU/SKU CRUD |
| cart-service | 8084 | 购物车（Redis） |
| order-service | 8085 | 订单管理（含状态机） |
| inventory-service | 8086 | 库存扣减（Redis Lua） |
| payment-service | 8087 | 沙箱支付 + 对账补偿 |
| marketing-service | 8088 | 优惠券/折扣/拼团/秒杀 |
| logistics-service | 8089 | 运费计算 + 物流轨迹 |
| aftersale-service | 8090 | 售后/逆向流程（状态机） |
| community-service | 8091 | 内容社区（笔记/短视频/直播） |
| life-service | 8092 | 本地生活（POI/到店预约） |
| closet-service | 8093 | 智能衣橱/家居盘点 |
| wallet-service | 8094 | 会员钱包（余额/等级/订阅） |
| ai-orchestrator | 8095 | AI 编排（Python FastAPI，SSE 对话） |

### 第 3 步：启动 AI 编排服务（需要 LLM Key）

```bash
pip install -r services/ai-orchestrator/requirements.txt

# 配置 LLM（OpenAI 兼容：阿里云百炼 / OpenAI 等）
cd services/ai-orchestrator
copy .env.example .env    # 然后编辑 .env 填入：
#   ZHIGOU_LLM_BASE_URL=https://api.openai.com/v1
#   ZHIGOU_LLM_API_KEY=sk-xxxx
#   ZHIGOU_LLM_MODEL=gpt-4o-mini
cd ../.. && python services/ai-orchestrator/main.py
```

> AI 服务**没有 LLM Key 也能启动**（走降级开关 `config/fallback.yml`），但对话体验会退化为规则应答；完整演示需真实 Key。

### 第 4 步：启动 BFF 与前端

```bash
# BFF 聚合层（NestJS, :3000）
cd apps/bff-shop && npm install && npm run start:dev

# 移动端 H5（Vue 3, :5173，已配 /api 代理到 BFF）
cd apps/h5-shop && npm install && npm run dev

# 商家后台（Element Plus）
cd apps/admin-merchant && npm install && npm run dev
```

### 第 5 步：验证服务就绪

```bash
# 健康检查
curl http://localhost:8080/actuator/health   # auth-center

# 用户注册 + 登录（验证码从 Redis 读取）
curl -X POST http://localhost:3000/auth/send-sms-code \
  -H "Content-Type: application/json" \
  -d '{"phone":"13800138000"}'
CODE=$(docker exec zhigou-redis redis-cli GET "auth:sms:13800138000")
curl -X POST http://localhost:3000/auth/login \
  -H "Content-Type: application/json" \
  -d "{\"phone\":\"13800138000\",\"code\":\"$CODE\"}"
# 返回 { token }，后续请求带 Authorization: Bearer $TOKEN
```

## 快速演示所有功能

**推荐路径**：中间件 + 后端 + AI + BFF + H5 全部启动后，打开 `http://localhost:5173`。

### 1. 登录演示

进入 `/login`，输入手机号 `13800138000` → 点击获取验证码 → 从 Redis 取码填入：

```bash
docker exec zhigou-redis redis-cli GET "auth:sms:13800138000"
```

> dev profile 内置 mock 数据（Flyway `db/mock` 仅 dev 执行），登录后即可看到完整商品/订单/店铺数据；清理 mock 数据用 `scripts/mock-data/clean-mock.ps1`。

### 2. H5 页面演示地图

| 页面 | 路由 | 演示什么 |
|------|------|----------|
| 首页 | `/` | AI 输入条、8 宫格功能入口、推荐 Banner、今日省钱卡片 |
| AI 对话 | `/chat` | 对话式购物「小智」：快捷卡片、SSE 流式回复、工具调用商品卡 |
| 商品/比价 | `/products` · `/product/:id` · `/compare` | 商品列表/详情、跨平台比价页 |
| 购物交易 | `/cart` → `/checkout` → `/orders` → `/order/:id` | 加购 → 结算 → 下单 → 沙箱支付 → 订单详情 |
| 售后 | `/aftersale/apply` | 申请售后（退货/退款） |
| 衣橱 | `/closet` | 拍照识别录入 + 每日穿搭 |
| 社区 | `/community` · `/short-video` · `/live` | 种草笔记、短视频、直播 |
| 本地生活 | `/life` | POI 到店、预约服务 |
| 拼团 | `/groupbuy` | 发起/参与拼团 |
| 钱包/会员 | `/wallet` | 余额、会员等级、AI 订阅 |
| 商家中心 | `/merchant` | 经营统计、AI 经营助手、预警、售后工作台 |
| 我的 | `/profile` · `/favorites` · `/history` · `/coupons` | 个人中心、收藏、足迹、优惠券 |
| 省钱助手 | `/cost-saver` | 比价/凑单/降价提醒/历史价格 |

### 3. AI 对话演示（试试这些问法）

在 `/chat` 输入，或直接带参数打开 `http://localhost:5173/chat?q=...`：

| 场景 | 示例问法 | 触发的 AI 能力 |
|------|----------|----------------|
| 商品推荐 | 「推荐跑步鞋」 | search_products + 商品卡 |
| 跨平台比价 | 「帮我比价 iPhone 16」 | compare_prices 比价 |
| 凑单省钱 | 「怎么凑单最省钱」 | optimize_cart 凑单优化 |
| 差评避坑 | 「这款有差评吗」 | review 分析 + 水军识别 |
| AI 送礼 | 「送女朋友生日礼物」 | gift_assistant 送礼建议 |
| 拼团 | 「有没有拼团活动」 | groupbuy_finder 拼团 |
| 售后 | 「我要退货」 | aftersale 售后流程 |
| 物流 | 「我的包裹到哪了」 | logistics_tracker 物流跟踪 |
| 补货 | 「XX 什么时候补货」 | restock 补货提醒 |
| 定时购 | 「每周帮我买两斤苹果」 | scheduled_buy 定时购（确认门） |
| 主动提醒 | 「有什么提醒吗」 | reminders 降价/复购/换季提醒 |
| 客服 | 「转人工客服」 | customer_service 工单 |
| 衣橱识别 | 「帮我识别这件衣服」 | closet_scan_item 视觉入库 |
| 每日穿搭 | 「今天穿什么」 | outfit_daily 穿搭建议 |
| 商家经营 | 「今天经营情况怎么样」 | merchant 经营统计/预警 |

### 4. 一键冒烟（全链路 17 步）

```bash
# 前置：16 个后端服务 + BFF + H5 全部启动
powershell -NoExecutionPolicy Bypass -File scripts/smoke/zhigou-e2e.ps1
# 期望输出：PASS=17 FAIL=0
```

### 5. 压测

```bash
k6 run scripts/load-test/smoke-test.js    # BFF 层快速冒烟压测
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
| 消息 | RocketMQ 5.x | 异步/最终一致性（outbox 模式） |
| 鉴权 | JWT (HS256) + Nimbus JOSE | 认证 |
| AI | Python 3.11 + FastAPI + LangChain 1.x | 对话/Agent/工具循环 |
| 监控 | Prometheus + Grafana + Loki | 可观测 |

### 前端

| 类别 | 技术 |
|------|------|
| 移动端 H5 | Vue 3 + Vite + TypeScript + Pinia + motion 动效 |
| 商家后台 | Vue 3 + Element Plus |
| BFF | NestJS 12 + TypeScript |
| SSE | fetch + ReadableStream (POST) |

### DevOps

| 类别 | 工具 |
|------|------|
| 编排 | Docker Compose |
| 部署 | K8s (Helm chart 开发中) |
| CI | GitHub Actions（build-test → 镜像 → 部署，13 checks 绿） |
| 迁移 | Flyway（含 mock 数据 profile） |

## 项目结构

```
zhigou/
├── services/                    # 微服务 (15 Java + 1 Python AI)
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
│   ├── community-service/       #   内容社区 :8091
│   ├── life-service/            #   本地生活 :8092
│   ├── closet-service/          #   智能衣橱/家居 :8093
│   ├── wallet-service/          #   会员钱包 :8094
│   └── ai-orchestrator/         #   AI 编排 (Python FastAPI) :8095
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
│   ├── smoke/                   #   全链路冒烟 (zhigou-e2e.ps1)
│   ├── load-test/               #   压测 (k6 / seed-data)
│   ├── mock-data/               #   mock 清除脚本
│   ├── ai-eval/                 #   AI 评测回归集（工具 schema/路由/注入防护）
│   └── *-fault-injection/       #   故障注入实测脚本（一致性/韧性/AI）
├── config/                      # 运行时配置
│   └── fallback.yml             #   AI 降级开关
├── conf/                        # 基础设施配置
│   ├── prometheus/              #   Prometheus 抓取配置
│   ├── grafana/                 #   Grafana 数据源+面板
│   └── loki/                    #   Loki 存储配置
└── assets/                      # 静态资源（logo）
```

## 测试

```bash
# Java 服务测试
mvn test -pl services/auth-center,services/product-service,services/order-service

# Python 测试（AI 编排：工具/路由/注入防护回归）
cd services/ai-orchestrator && pytest
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/ai-eval/run_eval.ps1   # AI 评测回归集（schema 35 / routing 44 / injection 19）

# 端到端冒烟（全链路 17 步黄金路径）
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/smoke/zhigou-e2e.ps1

# 压测
k6 run scripts/load-test/smoke-test.js
```

## 压测结果（BFF 层）

| 并发 | 总请求 | 错误率 | P50 | P95 | P99 | RPS |
|------|--------|--------|-----|-----|-----|-----|
| 5 | 150 | 0% | — | 40ms | — | — |
| 20 | 1,500 | 0% | 46ms | 102ms | 504ms | 387 |
| 50 | 4,500 | 0% | 77ms | 131ms | 142ms | 718 |

> M3 容量模型：BFF 横扩 1.83x（单 290 → 双 531 RPS 零错误）+ 读层 1263 RPS + 写链路瓶颈定位（order→product 级联超时）。

## 安全与配置说明

- **仓库内所有密码/密钥均为本地开发占位值**（`123456`、`sandbox-secret-key`、`change-me-to-a-random-256-bit-string`），真实配置通过未提交的 `.env` 注入
- 部署前必须：修改中间件密码、替换 `JWT_SECRET`、为 AI 服务配置真实 LLM Key、替换沙箱支付密钥为生产微信/支付宝 SDK
- 日志脱敏 AOP、XSS 注入防护、JWT 鉴权默认开启；AI 侧含 prompt 注入防护与工具调用权限门

## 红线

1. ❌ 不允许跨服务直连对方数据库
2. ❌ 不允许把密钥写入代码
3. ❌ 不允许跳过测试
4. ❌ 不允许暴露栈信息给前端
5. ❌ 不允许生产环境开 DEBUG 日志

## 贡献

1. Fork 仓库 → 新建分支 → 提交改动 → 发起 Pull Request
2. 请保证：新增/修改功能附带测试；CI（GitHub Actions，13 checks）全绿；涉及接口变更同步更新服务 README

## 许可证

MIT（LICENSE 文件待补充）

---

<p align="center">
  <sub>Built with ❤️ by the 智购 Team</sub>
</p>
