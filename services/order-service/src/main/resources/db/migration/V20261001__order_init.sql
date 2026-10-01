CREATE TABLE `order_main` (
    `id`              BIGINT AUTO_INCREMENT PRIMARY KEY,
    `order_id`        BIGINT NOT NULL UNIQUE COMMENT '业务订单号',
    `user_id`         BIGINT NOT NULL,
    `request_id`      VARCHAR(64) DEFAULT NULL COMMENT '幂等键',
    `order_status`    VARCHAR(32) NOT NULL DEFAULT 'INIT',
    `total_amount`    BIGINT NOT NULL DEFAULT 0 COMMENT '总金额（分）',
    `pay_amount`      BIGINT NOT NULL DEFAULT 0 COMMENT '实付（分）',
    `coupon_id`       BIGINT DEFAULT NULL,
    `receiver_name`   VARCHAR(64) DEFAULT NULL,
    `receiver_phone`  VARCHAR(255) DEFAULT NULL,
    `receiver_address` VARCHAR(512) DEFAULT NULL,
    `close_reason`    VARCHAR(256) DEFAULT NULL,
    `create_time`     DATETIME(3) DEFAULT CURRENT_TIMESTAMP(3),
    `update_time`     DATETIME(3) DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    `version`         INT DEFAULT 0,
    `deleted`         TINYINT DEFAULT 0,
    UNIQUE INDEX `uk_user_request` (`user_id`, `request_id`),
    INDEX `idx_user_id` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='订单主表';

CREATE TABLE `order_item` (
    `id`          BIGINT AUTO_INCREMENT PRIMARY KEY,
    `order_id`    BIGINT NOT NULL,
    `sku_id`      BIGINT NOT NULL,
    `spu_id`      BIGINT DEFAULT NULL,
    `sku_name`    VARCHAR(256) DEFAULT NULL,
    `spec`        VARCHAR(256) DEFAULT NULL,
    `price`       BIGINT NOT NULL COMMENT '单价（分）',
    `count`       INT NOT NULL DEFAULT 1,
    `create_time` DATETIME(3) DEFAULT CURRENT_TIMESTAMP(3),
    INDEX `idx_order_id` (`order_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='订单明细';

CREATE TABLE `outbox` (
    `id`          BIGINT AUTO_INCREMENT PRIMARY KEY,
    `message_id`  VARCHAR(64) NOT NULL UNIQUE,
    `topic`       VARCHAR(128) NOT NULL,
    `tag`         VARCHAR(64) DEFAULT NULL,
    `payload`     TEXT NOT NULL COMMENT '消息体 JSON',
    `status`      TINYINT DEFAULT 0 COMMENT '0待发送 1已发送',
    `create_time` DATETIME(3) DEFAULT CURRENT_TIMESTAMP(3)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='本地消息表';