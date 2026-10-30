-- ============================================================
-- 智购 · order-service：多商家隔离（merchant_id）
-- - order_main 新增 merchant_id：订单归属商家（多租户数据隔离）
-- - 现有订单（商家A 演示）回填 merchant_id=1；商家B 暂无订单
-- ============================================================

ALTER TABLE `order_main`
    ADD COLUMN `merchant_id` BIGINT DEFAULT NULL
        COMMENT '订单归属商家ID（多商家隔离）' AFTER `user_id`;

UPDATE `order_main` SET `merchant_id` = 1 WHERE `merchant_id` IS NULL;

ALTER TABLE `order_main` ADD INDEX `idx_merchant_status` (`merchant_id`, `order_status`);
