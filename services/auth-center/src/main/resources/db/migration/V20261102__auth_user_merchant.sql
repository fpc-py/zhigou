-- ============================================================
-- 智购 · auth-center：多商家隔离（merchant_id）
-- - user 表新增 merchant_id：MERCHANT 角色归属商家；USER 为 NULL
-- - 演示商家 A：13900000001 → merchant_id=1
-- - 演示商家 B：13900000002 → merchant_id=2（新增）
-- ============================================================

ALTER TABLE `user`
    ADD COLUMN `merchant_id` BIGINT DEFAULT NULL
        COMMENT '商家ID（MERCHANT 角色归属商家；USER 为 NULL）' AFTER `role`;

UPDATE `user` SET `merchant_id` = 1 WHERE `phone` = '13900000001' AND `role` = 'MERCHANT';

INSERT INTO `user` (`user_id`, `phone`, `role`, `merchant_id`)
VALUES (2099999999999999998, '13900000002', 'MERCHANT', 2)
ON DUPLICATE KEY UPDATE `role` = 'MERCHANT', `merchant_id` = 2;
