-- ============================================================
-- 智购 · auth-center 初始化迁移
-- 用户认证表（与 user-service user 表结构一致，用于登录认证）
-- ============================================================

CREATE TABLE `user` (
    `id`          BIGINT       AUTO_INCREMENT PRIMARY KEY,
    `user_id`     BIGINT       NOT NULL UNIQUE COMMENT '业务用户ID（分片键）',
    `phone`       VARCHAR(255) NOT NULL UNIQUE COMMENT '手机号',
    `create_time` DATETIME(3)  DEFAULT CURRENT_TIMESTAMP(3),
    `update_time` DATETIME(3)  DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    `version`     INT          DEFAULT 0,
    `deleted`     TINYINT      DEFAULT 0
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='用户认证表';