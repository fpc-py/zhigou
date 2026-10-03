-- ============================================================
-- 智购 · marketing-service mock 数据（仅 dev profile 执行）
-- 用户优惠券，coupon_template/promotion_rule 已在 DDL 迁移里
-- user_id 与 user mock 对齐
-- ============================================================
INSERT INTO `user_coupon` (user_id, coupon_template_id, status, source_order_id) VALUES
(9000000000000000001, 1, 'UNUSED', NULL),
(9000000000000000001, 2, 'UNUSED', NULL),
(9000000000000000002, 1, 'UNUSED', NULL),
(9000000000000000002, 3, 'UNUSED', NULL),
(9000000000000000003, 2, 'UNUSED', NULL),
(9000000000000000003, 3, 'UNUSED', NULL)
ON DUPLICATE KEY UPDATE status = VALUES(status);

-- mock 优惠快照
INSERT INTO `discount_snapshot` (order_no, total_amount, discount_amount, coupon_id, rule_ids, final_amount) VALUES
('9000000000000000100', 9900,  0,    NULL, '[1]', 9900),
('9000000000000000102', 4900,  500,  3,    '[]',  4400)
ON DUPLICATE KEY UPDATE final_amount = VALUES(final_amount);
