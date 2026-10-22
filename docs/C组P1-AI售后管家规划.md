# C 组 P1 · AI 售后管家（规划）

> 立项背景：用户拍板「都做，先售后管家」——AI 售后管家先行，Agent 定时购作 P1 二期（复用一键代下单确认流）。
> 对齐红线：冷启动为零 / 第一屏可感知 / 错误不骚扰 / 用户价值 > 商家价值 > 技术叙事。

## 1. 现状盘点（不重复造轮子）

| 底座 | 状态 | 说明 |
|---|---|---|
| `aftersale_assistant(issue, aftersale_no)` | ✅ 已交付 | 售后话术生成 + 单号进度查询（aftersale-service） |
| `logistics_tracker(shipment_no)` | ✅ 已交付 | 物流轨迹 + 延误评估（logistics-service） |
| `after_sale_repair(action, order_no, ...)` | ✅ 已交付 | 质保盘点 + 维修预约（aftersale-service warranty/repair） |
| aftersale-service 接口 | ✅ 已交付 | `POST /aftersale/apply`（发起售后）/ `GET /mine` / `GET /{no}` / warranty / repair |
| H5 对话快捷入口 | ✅ 已交付 | 售后助手/物流管家卡片（chat/index.vue quickActs） |

**缺口**：①售后场景缺「聚合诊断」——一次查清「我的售后单 + 物流异常 + 质保到期」；②缺「一键发起售后」的 AI 入口（现有话术只提示到订单页手动发起）；③缺模型级确认门（防未确认执行，呼应本轮刚修复的「模型谎报执行」坑）。

## 2. 本轮开发块（A 块 · AI 售后管家工具）

### A1 新工具 `after_sale_manager(action, issue, order_no, type, reason, confirm)`

- **action=overview（默认）**：聚合三路诊断
  1. `GET /aftersale/mine?userId=` → 我的售后单（处理中/已完成）
  2. `GET /logistics/delay-alerts?userId=` → 物流延误预警（物流管家二期已有）
  3. `GET /aftersale/warranty/alerts?userId=&days=30` → 质保 30 天内到期/已过期
  → 输出「售后健康度」：N 单售后处理中、M 单物流异常、K 件质保临近 + 每项下一步建议（可追问详情）。
- **action=apply**：一键发起售后（**确认门**）
  - `confirm=false`（默认）→ 返回申请摘要 + 提示「回复『确认申请』后我立即提交」；**不执行任何写操作**（错误不骚扰红线）。
  - `confirm=true` → ① `GET order-service /{orderNo}` 取真实订单金额（**防编造金额**）→ ② `POST /aftersale/apply`（orderNo/type/reason/amount）→ 返回售后单号 + 状态。
  - 写操作带 `_http_post`（无重试，防重复创建）。

### A2 路由与提示词

- `system.md` 工具表追加 `after_sale_manager` 行（overview/apply + 确认门说明）。
- `_ROUTE_RULES` 新增售后管家触发词，**置于 aftersale_assistant 之前**（聚合优先）：
  `售后管家|处理售后|我的售后|售后情况|全部售后|售后汇总|我要退款|我要退货|我要换货|申请退款|帮我退款|物流卡住|快递没到|一直没到|催一下物流`

### A3 测试

- pytest 用例（mock _http_get/_http_post）：overview 三路聚合、空数据降级、apply 未确认不执行、apply 确认后真实提交（金额取订单服务）、坏参数友好降级。
- 真实链路验证（起 8090 aftersale + 8085 order）：造售后单 → overview 命中；apply 确认 → 售后单创建。

## 3. 后续块（P1 二期 · Agent 定时购，待拍板后开发）

- 复用一键代下单确认流 + cron 任务底座；用户设定「每周自动买 XX」→ 定时任务 → 到点生成订单草稿 → 用户确认 → 下单。

## 4. 验收标准

- [ ] overview 三路聚合真实数据、下一步建议可执行
- [ ] apply 未确认不写库（确认门生效）
- [ ] apply 确认后订单金额来自 order-service（非模型编造）
- [ ] pytest 全绿；三件套同步；每块独立提交
