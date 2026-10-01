CREATE TABLE `file_meta` (
    `id`            BIGINT       AUTO_INCREMENT PRIMARY KEY,
    `file_id`       VARCHAR(128) NOT NULL UNIQUE COMMENT '业务文件ID',
    `user_id`        BIGINT       NOT NULL        COMMENT '上传者',
    `object_key`    VARCHAR(256) NOT NULL        COMMENT 'MinIO object key',
    `original_name` VARCHAR(256) DEFAULT NULL    COMMENT '原始文件名',
    `size`          BIGINT       DEFAULT 0       COMMENT '文件字节数',
    `mime_type`     VARCHAR(64)  DEFAULT NULL    COMMENT 'MIME 类型',
    `create_time`   DATETIME(3)  DEFAULT CURRENT_TIMESTAMP(3),
    `update_time`   DATETIME(3)  DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    `version`       INT          DEFAULT 0,
    `deleted`       TINYINT      DEFAULT 0,
    INDEX `idx_user_id` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='文件元信息表';