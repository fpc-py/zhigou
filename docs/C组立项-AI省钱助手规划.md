# C 组立项 · AI 省钱助手（P0）开发规划

> 日期：2026-10-22 · 版本：[0.2.62] 规划（决策已拍板：比价+凑单+降价提醒/历史价格全包含；衣橱零录入版保留 P2）
> 基线：`docs/C组差异化立项分析.md` §8（用户视角修正：冷启动为零 / 第一屏可感知 / 错误不骚扰）
> 纪律：每块独立提交 + 三件套 + 实测数据；借力不造轮子

---

## 1. 目标与验收

**一句话**：让用户打开省钱助手第一眼就看到「本月帮你省了 X 元」，并能一句话关注降价、一键按最优方案结算。

| 验收项 | 标准（带实测数据） |
|---|---|
| 历史价格 | 在售 SKU 每日快照入库；`GET /price/history?skuId=` 返回 7/30 天趋势 + 历史最低价（对比现价算出「比最低贵 ¥X」） |
| 降价提醒 | 用户**主动关注**（targetPrice 可选）→ 每日检测 → 降价命中写站内信（`price_notice`），H5 红点可见；**不骚扰=关注制，默认零推送** |
| 省钱汇总 | `GET /cost-saver/overview`：本月累计节省（券实省+比价最优差+降价已省）+ 进行中降价提醒 + 最优凑单建议；H5 `/cost-saver` 第一屏可感知 |
| AI 对话 | ai-orchestrator 新增 `price_alert` 工具：关注/取消/查降价；system.md 触发词接入 |
| 冒烟闭环 | 造数→采集→关注→模拟降价→提醒生成→overview 汇总 全链路 1 条命令可复验 |

---

## 2. 模块拆分（每块独立提交）

| 块 | 内容 | 归属 | 复用底座（不重复造） |
|---|---|---|---|
| A | **历史价格底座**：`price_history` 表（Flyway）+ 每日采集定时任务（@Scheduled，在售 SKU 快照）+ 下单/浏览实时补点 + `GET /price/history`（趋势/最低价） | product-service | ProductSkuMapper、现有 @Scheduled 模式（order 关单任务） |
| B | **降价关注与提醒**：`price_alert` 关注表 + `price_notice` 站内信表 + 关注/取消/列表接口 + 每日检测任务（比对 price_history 最新 vs 关注时价） | product-service | A 块历史价格 |
| C | **省钱助手汇总 + BFF + H5**：`GET /cost-saver/overview`（组合：券实省=marketing、比价差=price/compare、降价=price_notice、凑单=discount/calculate）→ BFF 透传 `/cost-saver/*` → H5 `/cost-saver` 页（第一屏总节省+提醒列表+凑单建议+关注入口） | product-service + BFF + H5 | 现有透传模式、optimize_cart 同款组合逻辑 |
| D | **AI 工具增强**：`price_alert` 工具（关注/取消/查询降价）+ `system.md` 触发词 + pytest | ai-orchestrator | compare_prices 工具样板 |
| E | **验证 + 三件套**：造数脚本 + 冒烟断言（采集→关注→降价→提醒→overview）+ CHANGELOG/progress/bug + 提交推送 | 全链路 | %TEMP% 既有冒烟模板 |

---

## 3. 数据模型（product-service 分库 zhigou_product）

```sql
-- 历史价格快照（A 块）
CREATE TABLE price_history (
    id          BIGINT PRIMARY KEY AUTO_INCREMENT,
    sku_id      BIGINT NOT NULL,
    price       BIGINT NOT NULL COMMENT '售价(分)',
    snapshot_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    KEY idx_sku_time (sku_id, snapshot_at)
) COMMENT='SKU 历史价格快照';

-- 降价关注（B 块）
CREATE TABLE price_alert (
    id           BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id      BIGINT NOT NULL,
    sku_id       BIGINT NOT NULL,
    target_price BIGINT NULL COMMENT '期望价(分)，NULL=任意降价都提醒',
    watch_price  BIGINT NOT NULL COMMENT '关注时价格(分)',
    status       VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',  -- ACTIVE/PAUSED/OFF
    created_at   DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at   DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_user_sku (user_id, sku_id)
) COMMENT='用户降价关注';

-- 降价站内信（B 块）
CREATE TABLE price_notice (
    id         BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id    BIGINT NOT NULL,
    sku_id     BIGINT NOT NULL,
    old_price  BIGINT NOT NULL,
    new_price  BIGINT NOT NULL,
    read_flag  TINYINT NOT NULL DEFAULT 0,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    KEY idx_user_read (user_id, read_flag, created_at)
) COMMENT='降价提醒站内信';
```

---

## 4. 接口清单

| 接口 | 方法 | 说明 | 归属 |
|---|---|---|---|
| `/price/history?skuId=&days=` | GET | 趋势（每日价格序列）+ 最低价 + 现价对比 | product A |
| `/price/alert` | POST | 关注（body: skuId, targetPrice?）幂等 upsert | product B |
| `/price/alert/{skuId}` | DELETE | 取消关注 | product B |
| `/price/alert/mine` | GET | 我的关注列表（含当前价/最低价/是否已降） | product B |
| `/price/notice/mine?unreadOnly=` | GET | 降价提醒站内信 | product B |
| `/cost-saver/overview` | GET | 省钱汇总（本月节省 + 提醒 + 凑单） | product C |
| BFF `/cost-saver/*` + `/price/alert/*` | 透传 | X-User-Id 注入 + rawBody（19 位 id 纪律） | BFF C |

**19 位 id 纪律延续**：所有响应 id 字段 @JsonSerialize(ToStringSerializer)；BFF 透传 body 用 rawBody 字符串。

---

## 5. 面试叙事点（规划期预埋）

1. **关注制触达设计**：主动提醒默认零推送——「错误不骚扰」从架构层保证（不是开关，是数据模型约束：无关注记录=无提醒）。
2. **历史价格采集的工程权衡**：每日全量快照 + 交易实时补点（下单/浏览写 point-in-time 价格），兼顾完整性与冷启动（新 SKU 无快照也有价格基线）。
3. **省钱汇总的组合正确性**：券实省（marketing 试算）+ 比价最优差（多 adapter）+ 降价已省（历史最低差）三个口径独立计算再合并，避免重复计入（同一笔不能既算券又算比价差）——口径即面试深度。
4. **实测数据**：采集任务耗时、检测任务吞吐、overview P95——压测可复现。

---

## 6. 明确不做（本轮红线）

- 不做价格预测/趋势推断（只做事实快照与对比）；
- 不做全量用户推送（提醒仅关注制）；
- 不做跨平台真实爬价（沿用既有 PriceSourceAdapter 沙箱/模拟渠道）；
- 不做衣橱（P2 保留，零录入设计另行规划）。
