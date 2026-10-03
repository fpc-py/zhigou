-- ============================================================
-- 智购 · file-service mock 数据（仅 dev profile 执行）
-- 文件元数据，user_id 与 user mock 对齐
-- ============================================================
INSERT INTO `file_meta` (file_id, user_id, object_key, original_name, size, mime_type) VALUES
('MOCK_FILE_001', 9000000000000000001, 'mock/20261003/mock_pic_01.png', 'mock_pic_01.png', 102400, 'image/png'),
('MOCK_FILE_002', 9000000000000000002, 'mock/20261003/mock_pic_02.png', 'mock_pic_02.png', 204800, 'image/png'),
('MOCK_FILE_003', 9000000000000000003, 'mock/20261003/mock_pic_03.png', 'mock_pic_03.png', 51200,  'image/png')
ON DUPLICATE KEY UPDATE original_name = VALUES(original_name);
