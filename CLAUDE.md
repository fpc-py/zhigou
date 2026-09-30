# 智购项目 · CLAUDE.md

> 本文件是 Claude Code 在本仓库工作时必须遵守的硬约束。每次新会话启动，Claude Code 会自动读本文件。
> 你（人类开发者）发现它做错事，就把规则补在这里；它下次就不会再犯。

## 项目一句话

智购：AI 原生超级商城。技术方案见 `docs/智购-企业级工程化技术方案.md`；原型见`docs/智购AI超级商城-企业级可交互原型.html`；功能见`docs/智购功能文档.md`。

## 技术栈（硬约束，不要擅自更换）

- 后端服务：**Java 21 + Spring Boot 3.5.x + Spring Cloud Alibaba 2023.0.x**
- ORM：MyBatis-Plus 3.5.x；数据库：MySQL 8.0
- 缓存/分布式锁：Redis 7（Spring Data Redis）
- 消息队列：RocketMQ 5.x
- AI 服务：Python 3.11 + FastAPI + LangChain 1.x
- 前端：Vue 3 + Vite + TypeScript + Pinia + Element Plus
- 部署：Docker Compose（本地）+ Kubernetes（生产）
- 鉴权：JWT（OAuth2.1 风格，access 2h / refresh 14d）

**引入任何新中间件/新框架之前，必须先问我。**

## 目录结构

```
zhigou/
├── CLAUDE.md                  # 本文件
├── pom.xml                    # Maven 父 POM
├── services/                  # 每个微服务一个目录
│   ├── auth-center/
│   ├── user-service/
│   ├── product-service/
│   ├── cart-service/
│   ├── order-service/
│   ├── inventory-service/
│   ├── payment-service/
│   ├── marketing-service/
│   ├── logistics-service/
│   └── aftersale-service/
├── ai-services/
│   └── ai-orchestrator/       # Python FastAPI
├── apps/
│   ├── bff-shop/              # BFF 聚合层
│   └── admin-merchant/        # 商家后台前端
├── packages/
│   ├── proto/                 # 公共契约（OpenAPI / proto）
│   └── common/                # 公共 Java 库（Result、异常、工具）
├── prompts/                   # AI 系统提示词
├── infra/
│   ├── compose/               # docker-compose
│   └── helm/                  # K8s chart
├── docs/
│   ├── adr/                   # 架构决策记录
│   ├── progress.md            # 当前进度
│   └── load-test/             # 压测报告
└── scripts/                   # 端到端脚本、工具
```

## 代码规范

### Java
- 包名：`com.zhigou.<svc>`
- 分层：`controller / service / mapper / entity / dto / config`
- 所有对外返回统一 `Result<T> { code, message, data }`
- 业务异常抛 `BizException(code, message)`，由全局异常处理器转 `Result.fail`
- 金额字段一律 `Long` 存**分**，禁止用 `Double`/`Float`
- 时间字段用 `LocalDateTime`，DB 统一 `DATETIME(3)`
- 所有写接口必须带 `requestId`（String，32 位），服务端建唯一索引 `(user_id, request_id)` 防重

### API
- RESTful：`GET /resource`、`POST /resource`、`PUT /resource/{id}`、`DELETE /resource/{id}`
- 路径用复数名词，动作用 HTTP 方法表达，不要 `/getOrderById` 这种 RPC 风格
- 分页参数：`pageNum` / `pageSize`，默认 1/20，最大 100
- 用 Knife4j 注解写文档，启动后访问 `/doc.html`

### 日志
- 必须结构化 JSON，带 `traceId / userId / requestId`（脱敏后）
- **禁止打印**：手机号、身份证、地址、支付密码、token、完整请求体
- 错误日志要带上下文参数，不要只打 `e.getMessage()`

### 数据
- 所有表必须有：`id BIGINT PK AUTO_INCREMENT`、`create_time`、`update_time`、`version`（乐观锁）、`deleted TINYINT`（逻辑删）
- 金额：`BIGINT` 存分
- 时间：`DATETIME(3)`
- 迁移脚本用 Flyway：`V<date>__<desc>.sql`，不要手动改库

## 红线（违反直接打回）

1. **不允许跨服务直连对方数据库**——跨域操作走 HTTP 或 MQ。
2. **不允许把密钥/密码/API key 写进代码或提交进 git**——用环境变量，仓库里只放 `.env.example`。
3. **不允许跳过测试**。新代码必须有单测，关键路径（下单/支付/库存）必须用 Testcontainers 起真实中间件。
4. **不允许一个 commit 超过 500 行**（自动生成代码除外）。
5. **不允许在业务代码里硬配置**——配置走 `application-{env}.yml` 或 Nacos。
6. **不允许把栈信息/内部 SQL/异常类名返回给前端**。
7. **不允许在生产配置里把日志级别开到 DEBUG**。

## 常用命令

```bash
# 启动中间件（MySQL/Redis/RocketMQ/MinIO）
docker compose -f infra/compose/middleware.yml up -d

# 单测某个服务
./mvnw -pl services/order-service test

# 构建
./mvnw -DskipTests package

# 前端
cd apps/bff-shop && pnpm install && pnpm dev

# 端到端冒烟（W6 之后才有）
bash scripts/e2e-order.sh
```

## 提交规范

Conventional Commits：

```
feat(order): 新增创建订单接口，支持 requestId 幂等
fix(payment): 修复沙箱支付回调重复消费问题
refactor(inventory): 把 Lua 脚本抽到独立类
test(cart): 补充购物车并发加购测试
docs: 更新 ADR-0002
chore: 升级 spring-boot 到 3.2.5
```

每次提交必须能独立通过构建。

## 你（Claude Code）的工作方式

1. 开工前先读 `docs/progress.md`，了解当前进度。
2. 一个任务一件事，做完跑测试再 commit。
3. 改代码前先读相关文件，复述你要改什么。
4. 不要过度设计：先写能跑的最简单实现，后面再重构。
5. 跑不通就把完整报错贴出来，不要假装跑过。
6. 遇到你不确定的决策，问我，不要替我拍板。

