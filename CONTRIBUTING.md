# 智购 · 贡献指南（CONTRIBUTING）

> 本文档定义代码提交、评审、发布的协作纪律。硬约束以 `CLAUDE.md` 与 `.claude/rules/*` 为准，此处是操作流程。

## 1. 分支策略

- `main`：生产就绪分支，只接受 PR 合入，禁止直接推送
- `develop`：集成分支，各 feature 合入后在此验证
- `feature/<服务名>-<简述>`：功能分支，从 develop 切出
- `fix/<服务名>-<简述>`：修复分支
- `hotfix/<简述>`：紧急修复，从 main 切出，修完合回 main 和 develop

## 2. 提交规范

使用 [Conventional Commits](https://www.conventionalcommits.org/)：

```
feat(order): 新增创建订单接口，支持 requestId 幂等
fix(payment): 修复沙箱支付回调重复消费问题
refactor(inventory): 把 Lua 脚本抽到独立类
test(cart): 补充购物车并发加购测试
docs: 更新 ADR-0002
chore: 升级 spring-boot 到 3.5.1
```

规则：
- 每次提交必须能独立通过构建
- 单次 commit 不超过 500 行（自动生成代码除外）
- 禁止一个 commit 包含多个不相关的改动
- 一个任务一件事：做完 → 跑测试 → 再 commit

## 3. 开发流程

1. 开工前读 `docs/README.md`（文档导航）、`docs/progress.md`（进度）、`CLAUDE.md`（硬约束）
2. 从 `develop` 切 `feature/<服务名>-<简述>`
3. 改代码前先读相关文件，说明要改什么
4. 遵守 `.claude/rules/*`：安全 / Git / 可观测
5. 完成一个可验证单元后：
   - 跑对应构建与测试（见 §4）
   - 更新 `docs/progress.md` + `CHANGELOG.md`
   - 涉及接口/端口/目录变更：同步 `README.md`、`docs/README.md` 索引与受影响的文档

## 4. 验证要求（提交前必须全过）

| 变更类型 | 必须通过 |
|---|---|
| Java 服务 | `mvn -pl services/<svc> compile` + 关键路径单测 `mvn -pl services/<svc> test` |
| BFF | `cd apps/bff-shop && npm run build` |
| 前端 H5 | `cd apps/h5-shop && npm run build`（= type-check + build-only） |
| 商家后台 | `cd apps/admin-merchant && npm run build` |
| AI 服务 | `cd services/ai-orchestrator && pytest` |
| 端到端 | `bash scripts/e2e-order.sh` |

## 5. PR 流程

1. 本地跑通全量测试（§4）
2. 推 feature 分支
3. 创建 PR，填写：做了什么 / 怎么验证 / 测试结果
4. 至少一人 Review（可让 AI 辅助 review，发 `/review-pr`）
5. 按 `.claude/agents/code-reviewer.md` 清单自检：鉴权 / 事务 / 幂等 / SQL / 异常 / 日志 / 金额类型
6. Review 通过后 Squash Merge 到 develop

## 6. 文档纪律

- 所有文档索引见 `docs/README.md`；新文档必须登记
- 事实单一来源：端口/目录/接口清单只在一处维护，其他文档用链接
- 发现文档与代码矛盾：修正文档（或修正代码），并同步索引
- 重要技术选型：新增 `docs/adr/NNNN-<short>.md`，改决策用新 ADR supersede

## 7. 发布流程

- 发布前逐项检查 `docs/release-checklist.md`（代码冻结 / 版本 / DB / 配置 / 构建 / 部署 / 监控 / 冒烟 / 回滚）
- 镜像 tag 使用 git SHA，禁止 `latest`
- 出问题回滚：`git revert` + K8s rollout undo，不直接操作容器
