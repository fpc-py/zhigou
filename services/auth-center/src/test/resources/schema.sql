-- 与生产迁移 V20261001__auth_init.sql 结构一致（补齐 user_id 列）
CREATE TABLE IF NOT EXISTS `user` (
    `id`          BIGINT       AUTO_INCREMENT PRIMARY KEY,
    `user_id`     BIGINT       NOT NULL UNIQUE COMMENT '业务用户ID（分片键）',
    `phone`       VARCHAR(255) NOT NULL UNIQUE COMMENT '手机号',
    `create_time` DATETIME(3)  DEFAULT CURRENT_TIMESTAMP(3),
    `update_time` DATETIME(3)  DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    `version`     INT          DEFAULT 0,
    `deleted`     TINYINT      DEFAULT 0
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
