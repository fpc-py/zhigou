# 智购项目 · CLAUDE.md

> 本文件是 AI 协作与项目开发的**硬约束入口**。开工前必读；与代码事实冲突时，以本文件为准修正代码，或更新本文件（须同步 `docs/README.md` 索引）。

## 项目一句话

智购：**AI 原生超级购物生态 APP**——以个人智能购物助理「小智」为核心入口，重构「人-货-场」关系，实现从「人找货」到「AI 找人货匹配」的范式跃迁（产品愿景详见 `docs/智购功能文档.md`）。当前已落地对话式购物 + 完整交易闭环。

**文档中心见 `docs/README.md`**（所有文档的索引与阅读路径）。技术方案见 `docs/智购-企业级工程化技术方案.md`；原型见 `docs/智购AI超级商城-企业级可交互原型.html`；功能见 `docs/智购功能文档.md`；进度见 `docs/progress.md`；差距与执行顺序见 `docs/智购开发任务差距分析报告.md`。

## 技术栈（硬约束，不要擅自更换）

- 后端服务：**Java 21 + Spring Boot 3.5.x + Spring Cloud Alibaba 2023.0.x**
- ORM：MyBatis-Plus 3.5.x；数据库：MySQL 8.0
- 缓存/分布式锁：Redis 7（Spring Data Redis）
- 消息队列：RocketMQ 5.x
- AI 服务：Python 3.11 + FastAPI + LangChain 1.x（`services/ai-orchestrator`）
- 前端：Vue 3 + Vite + TypeScript + Pinia + Element Plus
- BFF 聚合层：NestJS 12 + TypeScript（`apps/bff-shop`，:3000）
- 部署：Docker Compose（本地）+ Kubernetes（生产）
- 鉴权：JWT（OAuth2.1 风格，access 2h / refresh 14d）

**引入任何新中间件/新框架之前，必须先问。**

## 目录结构

```
zhigou/
├── CLAUDE.md                  # 本文件
├── README.md                  # 项目门户
├── CONTRIBUTING.md            # 贡献规范
├── CHANGELOG.md               # 变更记录
├── pom.xml                    # Maven 父 POM
├── services/                  # 每个微服务一个目录（16 个）
│   ├── auth-center/           #   :8080 认证中心
│   ├── user-service/          #   :8081 用户
│   ├── file-service/          #   :8082 文件
│   ├── product-service/       #   :8083 商品
│   ├── cart-service/          #   :8084 购物车
│   ├── order-service/         #   :8085 订单
│   ├── inventory-service/     #   :8086 库存
│   ├── payment-service/       #   :8087 支付
│   ├── marketing-service/     #   :8088 营销
│   ├── logistics-service/     #   :8089 物流
│   ├── aftersale-service/     #   :8090 售后
│   ├── community-service/       #   :8091 内容社区
│   ├── life-service/            #   :8092 本地生活
│   ├── closet-service/          #   :8093 智能衣橱/家居
│   ├── wallet-service/          #   :8094 会员钱包
│   └── ai-orchestrator/         #   :8095 AI 编排 (Python FastAPI)
├── apps/
│   ├── bff-shop/              # BFF 聚合层 (NestJS, :3000)
│   ├── h5-shop/               # 移动端 H5 (Vue 3, :5173)
│   └── admin-merchant/        # 商家后台前端
├── packages/
│   ├── proto/                 # 公共契约（OpenAPI / proto）
│   └── common/                # 公共 Java 库（Result、异常、工具）
├── prompts/                   # AI 系统提示词（根目录为 C 端导购）
├── config/                    # 运行时配置（fallback.yml AI 降级开关）
├── conf/                      # 可观测配置（prometheus/grafana/loki）
├── infra/
│   ├── compose/               # docker-compose（中间件 + 监控）
│   └── helm/                  # K8s chart（WIP）
├── scripts/                   # e2e / 压测 / mock 清理脚本
├── docs/
│   ├── README.md              # 文档中心索引（必读）
│   ├── adr/                   # 架构决策记录
│   ├── progress.md            # 当前进度（唯一真相）
│   ├── load-test/             # 压测报告
│   ├── release-checklist.md   # 发布检查清单
│   └── ...                    # 产品/架构/开发/运维/战略文档
└── .claude/                   # AI 规则/命令/子代理/skills
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
- Snowflake ID 序列化给前端时用字符串（`@JsonSerialize(ToStringSerializer.class)`），避免 JS 精度丢失

### API
- RESTful：`GET /resource`、`POST /resource`、`PUT /resource/{id}`、`DELETE /resource/{id}`
- 路径用复数名词，动作用 HTTP 方法表达，不要 `/getOrderById` 这种 RPC 风格
- 分页参数：`pageNum` / `pageSize`，默认 1/20，最大 100
- 用户身份：BFF 从 JWT 解析 userId 后透传 `x-user-id` header 给下游；Java 服务优先读 `x-user-id`（回退默认值）
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
- Mock 数据放 `db/mock/`（仅 dev profile），版本号必须高于所有 DDL 迁移

## 红线（违反直接打回）

1. **不允许跨服务直连对方数据库**——跨域操作走 HTTP 或 MQ。
2. **不允许把密钥/密码/API key 写进代码或提交进 git**——用环境变量，仓库里只放 `.env.example`。
3. **不允许跳过测试**。新代码必须有单测，关键路径（下单/支付/库存）必须用 Testcontainers 起真实中间件。
4. **不允许一个 commit 超过 500 行**（自动生成代码除外）。
5. **不允许在业务代码里硬配置**——配置走 `application-{env}.yml` 或环境变量。
6. **不允许把栈信息/内部 SQL/异常类名返回给前端**。
7. **不允许在生产配置里把日志级别开到 DEBUG**。
8. **不允许移动/删除已发布的 Flyway 迁移脚本**——只允许新增更高版本号。

## 常用命令

```bash
# 启动中间件（MySQL/Redis/RocketMQ/MinIO/PostgreSQL）
docker compose -f infra/compose/middleware.yml up -d

# 编译单个服务
mvn -pl services/order-service compile

# 单测某个服务
mvn -pl services/order-service test

# 构建全部 Java 服务
mvn -DskipTests package

# AI 服务（venv + 8095，勿用系统 Python）
cd services/ai-orchestrator && .venv\Scripts\activate && uvicorn app.main:app --port 8095
# 或 python main.py（内部固定 8095）；改代码后需杀端口进程重启才生效

# BFF / 前端（注意：本仓库用 npm，不用 pnpm）
cd apps/bff-shop && npm install && npm run start:dev
cd apps/h5-shop && npm install && npm run dev

# 前端构建验证（type-check + build）
cd apps/h5-shop && npm run build

# 端到端冒烟
bash scripts/e2e-order.sh

# Mock 数据一键清除（上线前）
powershell -ExecutionPolicy Bypass -File scripts/mock-data/clean-mock.ps1
```

## 提交规范

Conventional Commits：

```
feat(order): 新增创建订单接口，支持 requestId 幂等
fix(payment): 修复沙箱支付回调重复消费问题
refactor(inventory): 把 Lua 脚本抽到独立类
test(cart): 补充购物车并发加购测试
docs: 更新 ADR-0002
chore: 升级 spring-boot 到 3.5.1
```

每次提交必须能独立通过构建。

## 规则文件（每次会话必须阅读）

以下文件包含与 CLAUDE.md 同等效力的硬约束，开工前必须读：

- `.claude/rules/security.md` — 安全红线（数据保护、鉴权、密钥、SQL）
- `.claude/rules/git-workflow.md` — Git 工作流（分支策略、PR 流程、回滚）
- `.claude/rules/observability.md` — 可观测性（日志格式、指标、链路追踪、告警）

## 你（AI 助手）的工作方式

1. 开工前先读 `docs/progress.md` 与 `docs/README.md`，了解当前进度与文档导航。
2. 一个任务一件事，做完跑测试再 commit。
3. 改代码前先读相关文件，复述你要改什么。
4. 不要过度设计：先写能跑的最简单实现，后面再重构。
5. 跑不通就把完整报错贴出来，不要假装跑过。
6. 遇到你不确定的决策，问，不要替我拍板。
7. 完成一个可验证单元后：更新 `docs/progress.md` + `CHANGELOG.md`；涉及接口/端口/目录变更时，同步 `README.md`、`docs/README.md` 与相关文档，防止文档失实。
