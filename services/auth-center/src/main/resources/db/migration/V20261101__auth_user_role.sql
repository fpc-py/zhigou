-- ============================================================
-- 智购 · auth-center：账号角色体系（RBAC 最小实现）
-- - user 表新增 role 列：USER=消费者（默认） / MERCHANT=商家
-- - 种子数据：演示商家账号 13900000001（role=MERCHANT）
--   消费者演示账号 13800138000 保持 USER，与商家账号彻底分离
-- ============================================================

ALTER TABLE `user`
    ADD COLUMN `role` VARCHAR(16) NOT NULL DEFAULT 'USER'
        COMMENT '账号角色：USER-消费者 / MERCHANT-商家' AFTER `phone`;

-- 演示商家账号（user_id 使用固定 Snowflake 值，避免与消费者冲突）
INSERT INTO `user` (`user_id`, `phone`, `role`)
VALUES (2099999999999999999, '13900000001', 'MERCHANT')
ON DUPLICATE KEY UPDATE `role` = 'MERCHANT';
