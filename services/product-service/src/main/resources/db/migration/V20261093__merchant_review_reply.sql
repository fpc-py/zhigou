-- ============================================================
-- 智购 · product-service 商家评论回复表（P2 3.3 AI 客服与营销一期）
-- 供商家评论管理：待回复列表 + AI 建议话术 + 提交回复闭环
-- ============================================================
CREATE TABLE `review_reply` (
    `id`            BIGINT AUTO_INCREMENT PRIMARY KEY,
    `review_id`     BIGINT NOT NULL UNIQUE COMMENT '被回复的评价 ID（Snowflake）',
    `merchant_id`   BIGINT NOT NULL DEFAULT 0 COMMENT '回复商家（演示：平台聚合商家）',
    `reply_content` VARCHAR(1024) NOT NULL COMMENT '商家回复正文',
    `status`        VARCHAR(16) NOT NULL DEFAULT 'REPLIED' COMMENT '状态：REPLIED=已回复',
    `create_time`   DATETIME(3) DEFAULT CURRENT_TIMESTAMP(3),
    `update_time`   DATETIME(3) DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    `deleted`       TINYINT DEFAULT 0,
    INDEX `idx_review` (`review_id`, `deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='商家评论回复';
