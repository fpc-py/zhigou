-- ============================================================
-- 智购 · user-service 初始化迁移
-- W1 单库单表，所有 user_id 查询都带 user_id（分片预埋）
-- ============================================================

CREATE TABLE `user` (
    `id`          BIGINT       AUTO_INCREMENT PRIMARY KEY,
    `user_id`     BIGINT       NOT NULL UNIQUE COMMENT '业务用户ID（分片键）',
    `phone`       VARCHAR(255) NOT NULL UNIQUE COMMENT '手机号（AES 加密）',
    `nickname`    VARCHAR(64)  DEFAULT NULL,
    `avatar_url`  VARCHAR(512) DEFAULT NULL,
    `gender`      TINYINT      DEFAULT 0    COMMENT '0未知 1男 2女',
    `birthday`    DATE         DEFAULT NULL,
    `level`       INT          DEFAULT 0    COMMENT '会员等级 0-5',
    `point`       INT          DEFAULT 0    COMMENT '积分',
    `create_time` DATETIME(3)  DEFAULT CURRENT_TIMESTAMP(3),
    `update_time` DATETIME(3)  DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    `version`     INT          DEFAULT 0,
    `deleted`     TINYINT      DEFAULT 0
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='用户表';

CREATE TABLE `address` (
    `id`             BIGINT       AUTO_INCREMENT PRIMARY KEY,
    `address_id`     BIGINT       NOT NULL UNIQUE COMMENT '业务地址ID',
    `user_id`        BIGINT       NOT NULL        COMMENT '分片键',
    `receiver_name`  VARCHAR(64)  DEFAULT NULL,
    `receiver_phone` VARCHAR(255) DEFAULT NULL    COMMENT '收货人手机号（AES 加密）',
    `province`       VARCHAR(128) DEFAULT NULL,
    `city`           VARCHAR(128) DEFAULT NULL,
    `district`       VARCHAR(128) DEFAULT NULL,
    `detail`         VARCHAR(512) DEFAULT NULL,
    `is_default`     TINYINT      DEFAULT 0      COMMENT '0否 1是',
    `create_time`    DATETIME(3)  DEFAULT CURRENT_TIMESTAMP(3),
    `update_time`    DATETIME(3)  DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    `version`        INT          DEFAULT 0,
    `deleted`        TINYINT      DEFAULT 0,
    INDEX `idx_user_id` (`user_id`, `deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='收货地址表';