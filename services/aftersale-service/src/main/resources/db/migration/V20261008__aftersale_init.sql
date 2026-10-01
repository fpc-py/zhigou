CREATE TABLE `aftersale_order` (
    `id`             BIGINT AUTO_INCREMENT PRIMARY KEY,
    `aftersale_no`   VARCHAR(64) NOT NULL UNIQUE,
    `order_no`       VARCHAR(64) NOT NULL,
    `user_id`         BIGINT NOT NULL,
    `type`           VARCHAR(32) NOT NULL COMMENT 'REFUND_ONLY/RETURN_GOODS/EXCHANGE',
    `reason`         VARCHAR(256),
    `amount`         BIGINT NOT NULL DEFAULT 0 COMMENT '申请退款金额(分)',
    `status`         VARCHAR(32) NOT NULL DEFAULT 'APPLYING',
    `images`         TEXT DEFAULT NULL COMMENT '凭证图片JSON',
    `reject_reason`  VARCHAR(256) DEFAULT NULL,
    `apply_at`       DATETIME(3) DEFAULT CURRENT_TIMESTAMP(3),
    `finish_at`      DATETIME(3) DEFAULT NULL,
    `create_time`    DATETIME(3) DEFAULT CURRENT_TIMESTAMP(3),
    `update_time`    DATETIME(3) DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    `version` INT DEFAULT 0, `deleted` TINYINT DEFAULT 0,
    INDEX `idx_order_no` (`order_no`), INDEX `idx_user_id` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='售后单';