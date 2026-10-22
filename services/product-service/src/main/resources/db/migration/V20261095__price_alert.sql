-- AI 省钱助手 · 降价关注与提醒（C 组 P0 B 块）
CREATE TABLE IF NOT EXISTS price_alert (
    id           BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id      BIGINT NOT NULL COMMENT '用户 ID',
    sku_id       BIGINT NOT NULL COMMENT 'SKU ID（Snowflake 19 位）',
    target_price BIGINT NULL COMMENT '期望价（分），NULL=任意降价都提醒',
    watch_price  BIGINT NOT NULL COMMENT '关注时价格（分），检测基准价',
    status       VARCHAR(16) NOT NULL DEFAULT 'ACTIVE' COMMENT 'ACTIVE / PAUSED / OFF',
    created_at   DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at   DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_user_sku (user_id, sku_id),
    KEY idx_status (status, updated_at)
) COMMENT = '用户降价关注（关注制：无关注记录=零推送）';

CREATE TABLE IF NOT EXISTS price_notice (
    id         BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id    BIGINT NOT NULL COMMENT '用户 ID',
    sku_id     BIGINT NOT NULL COMMENT 'SKU ID（Snowflake 19 位）',
    old_price  BIGINT NOT NULL COMMENT '降价前基准价（分）',
    new_price  BIGINT NOT NULL COMMENT '降价后价格（分）',
    read_flag  TINYINT NOT NULL DEFAULT 0 COMMENT '0 未读 / 1 已读',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    KEY idx_user_read (user_id, read_flag, created_at)
) COMMENT = '降价提醒站内信';
