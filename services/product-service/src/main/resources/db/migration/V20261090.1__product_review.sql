-- ============================================================
-- 智购 · product-service 商品评价表（P1 四批：评价数据底座）
-- 供 AI review_analysis 真实评价分析 + 差评/水军识别使用
-- ============================================================
CREATE TABLE `product_review` (
    `id`          BIGINT AUTO_INCREMENT PRIMARY KEY,
    `review_id`   BIGINT NOT NULL UNIQUE COMMENT '业务评价 ID（Snowflake）',
    `spu_id`      BIGINT NOT NULL COMMENT '商品 SPU ID',
    `user_id`     BIGINT NOT NULL COMMENT '评价用户 ID',
    `user_name`   VARCHAR(64) DEFAULT '' COMMENT '脱敏昵称',
    `rating`      TINYINT NOT NULL DEFAULT 5 COMMENT '1-5 星',
    `content`     VARCHAR(1024) NOT NULL COMMENT '评价正文',
    `images`      VARCHAR(2048) DEFAULT NULL COMMENT '晒图 URL，逗号分隔，空=无图',
    `is_mock`     TINYINT DEFAULT 1 COMMENT '1=演示数据 0=真实评价',
    `create_time` DATETIME(3) DEFAULT CURRENT_TIMESTAMP(3) COMMENT '评价时间（水军识别按此聚类）',
    `update_time` DATETIME(3) DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    `deleted`     TINYINT DEFAULT 0,
    INDEX `idx_spu_rating` (`spu_id`, `deleted`, `rating`),
    INDEX `idx_spu_time` (`spu_id`, `deleted`, `create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='商品评价';
