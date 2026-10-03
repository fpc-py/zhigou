-- ============================================================
-- 智购 · user-service mock 数据（仅 dev profile 执行）
-- 用户档案 + 收货地址，user_id 与 auth-center mock 对齐
-- ============================================================
INSERT INTO `user` (user_id, phone, nickname, avatar_url, gender, level, point) VALUES
(9000000000000000001, '13800138001', '测试用户01', 'https://example.com/avatar01.png', 1, 1, 100),
(9000000000000000002, '13800138002', '测试用户02', 'https://example.com/avatar02.png', 2, 2, 500),
(9000000000000000003, '13800138003', '测试用户03', 'https://example.com/avatar03.png', 0, 0, 0)
ON DUPLICATE KEY UPDATE nickname = VALUES(nickname);

INSERT INTO `address` (address_id, user_id, receiver_name, receiver_phone, province, city, district, detail, is_default) VALUES
(9000000000000000001, 9000000000000000001, '张三', '13800138001', '北京市', '北京市', '海淀区', '中关村大街1号', 1),
(9000000000000000002, 9000000000000000002, '李四', '13800138002', '上海市', '上海市', '浦东新区', '世纪大道100号', 1),
(9000000000000000003, 9000000000000000003, '王五', '13800138003', '广东省', '深圳市', '南山区', '科技园路8号', 1)
ON DUPLICATE KEY UPDATE receiver_name = VALUES(receiver_name);
