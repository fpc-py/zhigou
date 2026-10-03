-- ============================================================
-- 智购 · logistics-service mock 数据（仅 dev profile 执行）
-- 示例运单 + 轨迹，freight_template 已在 DDL 迁移里
-- order_no/user_id 与 order mock 对齐
-- ============================================================
INSERT INTO `shipment` (shipment_no, order_no, user_id, sender_addr, receiver_addr, weight_g, freight_fee, carrier, status, shipped_at) VALUES
('MOCK_SHIP_001', '9000000000000000100', 9000000000000000001, '北京市朝阳区发货仓', '{"name":"张三","phone":"13800138001","addr":"北京市海淀区中关村大街1号"}', 1000, 1000, 'SELF', 'SHIPPED', '2026-10-03 12:00:00'),
('MOCK_SHIP_002', '9000000000000000102', 9000000000000000003, '北京市朝阳区发货仓', '{"name":"王五","phone":"13800138003","addr":"广东省深圳市南山区科技园路8号"}', 500,  1000, 'SELF', 'DELIVERED', '2026-10-03 13:00:00')
ON DUPLICATE KEY UPDATE status = VALUES(status);

INSERT INTO `track_event` (shipment_no, node_time, node_name, description) VALUES
('MOCK_SHIP_001', '2026-10-03 12:00:00', '已发货', '商品已从仓库发出'),
('MOCK_SHIP_001', '2026-10-03 15:00:00', '运输中', '商品正在运输途中'),
('MOCK_SHIP_002', '2026-10-03 13:00:00', '已发货', '商品已从仓库发出'),
('MOCK_SHIP_002', '2026-10-03 18:00:00', '已签收', '买家已签收');
