CREATE TABLE `payment` (
    `id`          BIGINT AUTO_INCREMENT PRIMARY KEY,
    `payment_no`  VARCHAR(64) NOT NULL UNIQUE,
    `order_no`    VARCHAR(64) NOT NULL,
    `user_id`      BIGINT NOT NULL,
    `amount`      BIGINT NOT NULL COMMENT '支付金额（分）',
    `status`      VARCHAR(32) NOT NULL DEFAULT 'PENDING',
    `channel`     VARCHAR(32) DEFAULT 'SANDBOX',
    `paid_time`   DATETIME(3) DEFAULT NULL,
    `create_time` DATETIME(3) DEFAULT CURRENT_TIMESTAMP(3),
    `update_time` DATETIME(3) DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    `version`     INT DEFAULT 0,
    `deleted`     TINYINT DEFAULT 0,
    UNIQUE INDEX `uk_payment_no` (`payment_no`),
    INDEX `idx_order_no` (`order_no`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='支付表';