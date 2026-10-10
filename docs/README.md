# 智购 · 文档中心

> 本文档是智购项目全部文档的统一索引与规范。所有文档遵循统一的命名、定位与更新纪律。
> **任何新文档必须在此登记；任何文档状态变化必须同步更新此处。**

---

## 一、文档地图

按「域」组织。每个文档固定标注：**角色**（它回答什么问题）、**读者**、**更新频率**、**状态**。

| 域 | 文档 | 角色（回答什么） | 读者 | 更新频率 | 状态 |
|---|---|---|---|---|---|
| **入口** | [../README.md](../README.md) | 项目是什么、怎么跑起来 | 所有人 | 每次功能/结构变更 | ✅ 现行 |
| **入口** | [../CLAUDE.md](../CLAUDE.md) | AI 协作硬约束与规范入口 | AI 助手、开发者 | 规范/结构变更时 | ✅ 现行 |
| **入口** | [../CHANGELOG.md](../CHANGELOG.md) | 每个可交付单元改了什么 | 所有人 | 每次交付 | ✅ 现行 |
| **入口** | [../CONTRIBUTING.md](../CONTRIBUTING.md) | 怎么提交、怎么评审、怎么发布 | 开发者 | 流程变更时 | ✅ 现行 |
| **产品** | [智购功能文档.md](./智购功能文档.md) | 产品愿景：AI 原生超级购物生态、五大生态、AI 全链路体验 | 产品、研发、评审 | 里程碑评审时 | ✅ 现行 |
| **产品** | [智购AI超级商城-企业级可交互原型.html](./智购AI超级商城-企业级可交互原型.html) | H5 交互原型（已对齐实现） | 前端、产品 | 原型变更时 | ✅ 现行 |
| **架构** | [智购-企业级工程化技术方案.md](./智购-企业级工程化技术方案.md) | 生产级架构蓝图：NFR/微服务/一致性/安全/运维 | 架构、全栈、SRE | 技术决策变更时 | ✅ 现行 |
| **架构** | [真实支付接入指南.md](./真实支付接入指南.md) | 沙箱 → 微信/支付宝的改造实操（渠道/回调/密钥/前端/测试/上线） | payment/order/BFF/H5 开发者 | 支付渠道变更时 | ✅ 现行 |
| **架构** | [adr/](./adr/) | 为什么这样决策（ADR-0001 起） | 架构、AI 助手 | 每次架构决策 | ✅ 现行 |
| **开发** | [progress.md](./progress.md) | 当前进度、里程碑、待办池 | 所有人 | 每个可验证单元完成时 | ✅ 现行 |
| **开发** | [前端改造说明-原型对齐与后端对接.md](./前端改造说明-原型对齐与后端对接.md) | 前端 8 屏改造、BFF 接口清单、缺口与验证 | 前端、BFF 开发者 | 前端变更时 | ✅ 现行 |
| **开发** | [测试问题bug记录.md](./测试问题bug记录.md) | 已知 bug 与修复记录 | 开发者 | 每次修复 | ✅ 现行 |
| **开发** | [mock-data-cleanup-guide.md](./mock-data-cleanup-guide.md) | Mock 数据机制与上线清除 | 开发、运维 | 数据机制变更时 | ✅ 现行 |
| **测试** | [../scripts/smoke/zhigou-e2e.ps1](../scripts/smoke/zhigou-e2e.ps1) | 全链路联调冒烟脚本（17 步黄金路径，最新 PASS 17/17） | QA、全栈 | 每次联调 | ✅ 现行 |
| **运维** | [release-checklist.md](./release-checklist.md) | 发布前/中/后逐项检查（对齐 compose 蓝绿部署） | 发布负责人、QA | 每次发布 | ✅ 现行 |
| **运维** | [gray-release.md](./gray-release.md) | 灰度发布（服务分批放量）与回滚方案（镜像/DB/Hotfix） | 发布负责人、SRE | 发布流程变更时 | 🆕 新建 |
| **运维** | [monitoring-alerting.md](./monitoring-alerting.md) | 监控指标体系、SLO、告警定级规则与值班 SOP | 发布负责人、值班开发、SRE | 指标/告警变更时 | 🆕 新建 |
| **运维** | [load-test/](./load-test/) | 压测报告与结果数据 | 架构、SRE | 每次压测 | ✅ 现行 |
| **战略** | [智购开发任务差距分析报告.md](./智购开发任务差距分析报告.md) | 目标态 vs 现状的差距清单与执行顺序（P0→P4）+ 生产就绪度基线（附录 A） | 项目负责人、架构 | 每次差距评估 | ✅ 现行 |

> 状态图例：✅ 现行（与代码一致）/ 🔶 待更新（已知过时）/ 🆕 新建

---

## 二、按角色推荐的阅读路径

| 角色 | 阅读顺序 |
|---|---|
| **新成员/新会话（AI 助手）** | `README.md` → `CLAUDE.md` → `docs/README.md`（本文）→ `docs/progress.md` → 所属服务 README |
| **前端开发者** | `README.md` → `apps/h5-shop/README.md` → `docs/前端改造说明-原型对齐与后端对接.md` → `docs/智购AI超级商城-企业级可交互原型.html` |
| **后端开发者** | `CLAUDE.md` → `services/README.md` → 所属服务目录 → `docs/智购-企业级工程化技术方案.md`（第 3/4 章） |
| **架构/评审** | `docs/智购-企业级工程化技术方案.md` → `docs/adr/` → `docs/智购开发任务差距分析报告.md` |
| **发布负责人** | `docs/release-checklist.md` → `docs/gray-release.md` → `docs/monitoring-alerting.md` → `docs/智购开发任务差距分析报告.md`（附录 A）→ `docs/load-test/` |
| **产品/决策** | `docs/智购功能文档.md` → `docs/智购开发任务差距分析报告.md` → `docs/progress.md` |

---

## 三、文档规范

### 3.1 命名与组织

1. **域分组**：文档按 `docs/` 下的域组织——产品（愿景/原型）、架构（技术方案/ADR）、开发（进度/改造/测试）、运维（发布/就绪/压测）、战略（差距/路线）。
2. **命名**：中文名用于产品/战略类（读者是中文协作方），英文名用于工程运行类（`progress.md`/`release-checklist.md`/`load-test/`）。新增文档二选一，不要混用"中文-英文"拼接。
3. **一个文档一个角色**：回答"一个问题"。职责重叠时，新内容进对应文档，不要在多个文档重复维护同一事实。

### 3.2 内容纪律

4. **事实单一来源**：服务端口/目录结构/接口清单等事实，只在**一处**维护（README 的服务表、`docs/前端改造说明` 的接口清单），其他文档用链接引用，禁止复制粘贴。
5. **不写已过时的现状**：`progress.md` 是唯一进度真相；代码能力描述要与 `services/`、`apps/` 实际一致；发现矛盾立即修正对应文档。
6. **可验证**：文档中的命令、路径、端口必须真实可执行；引用文件不存在视为 bug。
7. **ADR 纪律**：重要技术选型必须新增 `docs/adr/NNNN-<short>.md`，改决策用新 ADR supersede，不改旧文件。

### 3.3 更新流程

8. 完成一个可验证单元 → 更新 `docs/progress.md` + `CHANGELOG.md` → 如涉及文档状态变化同步本索引。
9. 发布类变更（接口/端口/目录）必须同步更新受影响的文档，防止 `README`/`CLAUDE`/`release-checklist` 相互矛盾。
10. 每季度核对一次文档地图，清理失效引用与过时章节。

---

## 四、与代码的映射关系

```
zhigou/
├── README.md / CLAUDE.md / CONTRIBUTING.md / CHANGELOG.md   ← 项目入口
├── docs/                ← 本文档索引的全部文档
├── services/            ← 16 个服务（见 services/README.md）
├── apps/
│   ├── bff-shop/        ← BFF 聚合层（README 见 apps/bff-shop/README.md）
│   ├── h5-shop/         ← 移动端 H5（README 见 apps/h5-shop/README.md）
│   └── admin-merchant/  ← 商家后台（README 见 apps/admin-merchant/README.md）
├── packages/            ← common（Java 公共库）/ proto（契约）
├── infra/               ← compose / helm（WIP）
├── scripts/             ← e2e / 压测 / 数据脚本
├── conf/                ← Prometheus / Grafana / Loki 配置
├── config/              ← AI 降级开关 fallback.yml
└── prompts/             ← AI 导购系统提示词
```

*本文档路径: `docs/README.md`*
*更新频率: 文档体系变更时*
