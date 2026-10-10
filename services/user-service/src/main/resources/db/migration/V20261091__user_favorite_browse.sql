-- ============================================================
-- 智购 · user-service 用户画像底座
-- 收藏 + 浏览历史（upsert 幂等，商品信息冗余快照，展示免联表）
-- P0 技术收尾第 1 项：用户画像/收藏/浏览底座
-- ============================================================

CREATE TABLE `user_favorite` (
    `id`          BIGINT       AUTO_INCREMENT PRIMARY KEY,
    `user_id`     BIGINT       NOT NULL COMMENT '分片键',
    `spu_id`      BIGINT       NOT NULL COMMENT '商品 SPU ID',
    `sku_id`      BIGINT       DEFAULT NULL COMMENT '收藏时选中 SKU ID',
    `spu_name`    VARCHAR(255) DEFAULT NULL COMMENT '商品名快照',
    `price`       BIGINT       DEFAULT NULL COMMENT '收藏时价格快照（分）',
    `image_url`   VARCHAR(512) DEFAULT NULL COMMENT '商品图快照',
    `create_time` DATETIME(3)  DEFAULT CURRENT_TIMESTAMP(3),
    `update_time` DATETIME(3)  DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    `version`     INT          DEFAULT 0,
    `deleted`     TINYINT      DEFAULT 0,
    UNIQUE KEY `uk_user_spu` (`user_id`, `spu_id`, `deleted`),
    INDEX `idx_user_time` (`user_id`, `create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='用户收藏';

CREATE TABLE `user_browse_history` (
    `id`               BIGINT       AUTO_INCREMENT PRIMARY KEY,
    `user_id`          BIGINT       NOT NULL COMMENT '分片键',
    `spu_id`           BIGINT       NOT NULL COMMENT '商品 SPU ID',
    `sku_id`           BIGINT       DEFAULT NULL,
    `spu_name`         VARCHAR(255) DEFAULT NULL COMMENT '商品名快照',
    `price`            BIGINT       DEFAULT NULL COMMENT '浏览时价格快照（分）',
    `image_url`        VARCHAR(512) DEFAULT NULL COMMENT '商品图快照',
    `browse_count`     INT          DEFAULT 1 COMMENT '累计浏览次数',
    `last_browse_time` DATETIME(3)  DEFAULT CURRENT_TIMESTAMP(3) COMMENT '最近浏览时间',
    `create_time`      DATETIME(3)  DEFAULT CURRENT_TIMESTAMP(3),
    `update_time`      DATETIME(3)  DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    `version`          INT          DEFAULT 0,
    `deleted`          TINYINT      DEFAULT 0,
    UNIQUE KEY `uk_user_spu` (`user_id`, `spu_id`, `deleted`),
    INDEX `idx_user_time` (`user_id`, `last_browse_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='用户浏览历史';
