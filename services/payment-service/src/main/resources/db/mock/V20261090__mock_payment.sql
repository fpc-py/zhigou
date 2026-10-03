-- ============================================================
-- 智购 · payment-service mock 数据（仅 dev profile 执行）
-- 支付记录，order_no/user_id 与 order mock 对齐
-- ============================================================
INSERT INTO `payment` (payment_no, order_no, user_id, amount, status, channel, paid_time) VALUES
('MOCK_PAY_001', '9000000000000000100', 9000000000000000001, 9900,  'SUCCESS', 'SANDBOX', '2026-10-03 10:00:00'),
('MOCK_PAY_002', '9000000000000000102', 9000000000000000003, 4900,  'SUCCESS', 'SANDBOX', '2026-10-03 11:00:00')
ON DUPLICATE KEY UPDATE status = VALUES(status);
