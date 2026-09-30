# Git 工作流规范

> 此文件被 CLAUDE.md 引用。

## 分支策略

- `main`：生产就绪分支，只接受 PR 合入，禁止直接推送
- `develop`：集成分支，各 feature 合入后在此验证
- `feature/<服务名>-<简述>`：功能分支，从 develop 切出
- `fix/<服务名>-<简述>`：修复分支
- `hotfix/<简述>`：紧急修复，从 main 切出，修完合回 main 和 develop

## 提交规范

使用 Conventional Commits（与 CLAUDE.md 一致）：

```
feat(order): 新增创建订单接口，支持 requestId 幂等
fix(payment): 修复沙箱支付回调重复消费问题
refactor(inventory): 把 Lua 脚本抽到独立类
test(cart): 补充购物车并发加购测试
docs: 更新 ADR-0002
chore: 升级 spring-boot 到 3.5.1
```

规则：
- 每次提交必须能独立通过 `./mvnw -pl services/<svc> test`
- 单次 commit 不超过 500 行（自动生成代码除外）
- 禁止一个 commit 包含多个不相关的改动

## PR 流程

1. 本地跑通全量测试
2. 推 feature 分支
3. 创建 PR，填写：做了什么 / 怎么验证 / 测试结果截图
4. 至少一人 Review（可以让 Claude Code 辅助 review，发 `/review-pr`）
5. Review 通过后 Squash Merge 到 develop

## 回滚

- 每个服务镜像打 git SHA tag，不打 `latest`
- 出问题：`git revert <commit>`，不要手动改代码修复
- 生产回滚走 K8s rollout undo，不直接操作容器