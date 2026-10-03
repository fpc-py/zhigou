-- ============================================================
-- 智购 · aftersale-service mock 数据（仅 dev profile 执行）
-- 示例售后单，order_no/user_id 与 order mock 对齐
-- ============================================================
INSERT INTO `aftersale_order` (aftersale_no, order_no, user_id, type, reason, amount, status) VALUES
('MOCK_AS_001', '9000000000000000100', 9000000000000000001, 'REFUND_ONLY',  '尺寸不合适', 9900,  'REFUNDED'),
('MOCK_AS_002', '9000000000000000102', 9000000000000000003, 'RETURN_GOODS', '商品有瑕疵', 4900,  'APPLYING'),
('MOCK_AS_003', '9000000000000000101', 9000000000000000002, 'REFUND_ONLY',  '拍错了',     19900, 'SELLER_APPROVED')
ON DUPLICATE KEY UPDATE status = VALUES(status);
