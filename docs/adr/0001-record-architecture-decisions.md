# ADR-0001：记录架构决策

- 状态：Accepted
- 日期：2026-09-30
- 决策者：项目负责人

## 背景

我们需要一个轻量级的流程来记录"为什么这样做架构"。团队只有 1-3 人，且主力开发由 Claude Code 承担，更需要把决策显式写下来，避免下次开会话时反复纠结已经定过的事。

## 决策

采用 [MADR](https://adr.github.io/madr/) 风格的轻量级 ADR：

- 每个决策一个文件，放在 `docs/adr/NNNN-<short-title>.md`
- 编号递增，不改旧文件，决策变更用新 ADR  supersede 旧 ADR
- 模板固定五段：背景 / 决策 / 理由 / 后果 / 备选方案
- 不写冗长分析，一页纸能说清就行

## 理由

- 没有 ADR，三个月后会忘记"当时为什么选 RocketMQ 不选 Kafka"
- Claude Code 跨会话失忆，ADR 是它的长期记忆
- 小团队不需要重量级工具，Markdown 文件最易写

## 后果

- 所有重要技术选型必须配一份 ADR
- 新增文件 `docs/adr/0002-choose-spring-cloud-alibaba.md`（W1 完成时补）
- 改架构必须开新 ADR，不允许直接改旧的

## 备选方案

1. **不写 ADR，靠口口相传** —— 否决，跨会话必丢。
2. **用成熟 ADR 工具（adr-tools）** —— 过度工程，Markdown 文件够用。
