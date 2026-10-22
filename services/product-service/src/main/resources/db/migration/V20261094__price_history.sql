-- AI 省钱助手 · 历史价格快照（C 组 P0 A 块）
CREATE TABLE IF NOT EXISTS price_history (
    id          BIGINT PRIMARY KEY AUTO_INCREMENT,
    sku_id      BIGINT NOT NULL COMMENT 'SKU ID（Snowflake 19 位）',
    price       BIGINT NOT NULL COMMENT '售价（分）',
    snapshot_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '快照时间',
    KEY idx_sku_time (sku_id, snapshot_at),
    KEY idx_time (snapshot_at)
) COMMENT = 'SKU 历史价格快照（每日全量 + 实时补点）';
