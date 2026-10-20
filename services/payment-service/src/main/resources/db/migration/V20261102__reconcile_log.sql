-- ============================================================
-- 智购 · T+1 对账日志（A5）
-- payment_reconcile_log：每周期一条，汇总 + 差异清单 JSON，按周期幂等
-- ============================================================

CREATE TABLE `payment_reconcile_log` (
    `id`             BIGINT AUTO_INCREMENT PRIMARY KEY,
    `reconcile_date` DATE NOT NULL COMMENT '对账周期（自然日）',
    `total_count`    INT NOT NULL DEFAULT 0,
    `success_count`  INT NOT NULL DEFAULT 0,
    `pending_count`  INT NOT NULL DEFAULT 0,
    `refunded_count` INT NOT NULL DEFAULT 0,
    `diff_count`     INT NOT NULL DEFAULT 0,
    `summary_json`   TEXT COMMENT '汇总明细（JSON）',
    `diff_json`      TEXT COMMENT '差异清单（JSON：类型+单号+描述）',
    `compensated`    INT NOT NULL DEFAULT 0 COMMENT '本轮补偿动作数',
    `create_time`    DATETIME(3) DEFAULT CURRENT_TIMESTAMP(3),
    `update_time`    DATETIME(3) DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    UNIQUE KEY `uk_date` (`reconcile_date`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='支付对账日志';
