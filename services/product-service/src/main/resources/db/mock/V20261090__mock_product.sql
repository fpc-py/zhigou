-- ============================================================
-- 智购 · product-service mock 数据（仅 dev profile 执行）
-- 3 个 SPU + 6 个 SKU，category/brand 已在 DDL 迁移里
-- spu_id 段：9000000000000000010~012，sku_id 段：9000000000000000020~025
-- 图片使用可访问的生成式图片地址（原 example.com 为假地址，加载会被浏览器拦截）
-- ============================================================
INSERT INTO `product_spu` (spu_id, category_id, brand_id, name, subtitle, main_image, description, status, price_min, price_max, sales_volume) VALUES
(9000000000000000010, 1, 1, '测试商品-轻量运动鞋', '轻量透气，跑步利器',
 'https://trae-api-cn.mchost.guru/api/ide/v1/text_to_image?prompt=lightweight%20running%20shoes%20product%20photo%20white%20background&image_size=square',
 '轻量运动鞋，适合日常跑步', 1, 9900, 12900, 100),
(9000000000000000011, 2, 1, '测试商品-蓝牙耳机', '无线降噪，音质出众',
 'https://trae-api-cn.mchost.guru/api/ide/v1/text_to_image?prompt=wireless%20bluetooth%20earbuds%20charging%20case%20product%20photo%20white%20background&image_size=square',
 '蓝牙5.0降噪耳机', 1, 19900, 29900, 200),
(9000000000000000012, 1, 1, '测试商品-棉质T恤', '纯棉舒适，多色可选',
 'https://trae-api-cn.mchost.guru/api/ide/v1/text_to_image?prompt=plain%20cotton%20t%20shirt%20product%20photo%20white%20background&image_size=square',
 '100%棉T恤', 1, 4900, 6900, 500)
ON DUPLICATE KEY UPDATE name = VALUES(name), main_image = VALUES(main_image);

INSERT INTO `product_sku` (sku_id, spu_id, spec_name, spec_value, price, stock, image) VALUES
(9000000000000000020, 9000000000000000010, '颜色', '黑色', 9900,  100, 'https://trae-api-cn.mchost.guru/api/ide/v1/text_to_image?prompt=black%20running%20shoes%20product%20photo%20white%20background&image_size=square'),
(9000000000000000021, 9000000000000000010, '颜色', '白色', 12900, 80,  'https://trae-api-cn.mchost.guru/api/ide/v1/text_to_image?prompt=white%20running%20shoes%20product%20photo%20white%20background&image_size=square'),
(9000000000000000022, 9000000000000000011, '颜色', '黑色', 19900, 50,  'https://trae-api-cn.mchost.guru/api/ide/v1/text_to_image?prompt=black%20bluetooth%20earbuds%20product%20photo%20white%20background&image_size=square'),
(9000000000000000023, 9000000000000000011, '颜色', '白色', 29900, 30,  'https://trae-api-cn.mchost.guru/api/ide/v1/text_to_image?prompt=white%20bluetooth%20earbuds%20product%20photo%20white%20background&image_size=square'),
(9000000000000000024, 9000000000000000012, '颜色', '黑色', 4900,  200, 'https://trae-api-cn.mchost.guru/api/ide/v1/text_to_image?prompt=black%20cotton%20t%20shirt%20product%20photo%20white%20background&image_size=square'),
(9000000000000000025, 9000000000000000012, '颜色', '白色', 6900,  150, 'https://trae-api-cn.mchost.guru/api/ide/v1/text_to_image?prompt=white%20cotton%20t%20shirt%20product%20photo%20white%20background&image_size=square')
ON DUPLICATE KEY UPDATE price = VALUES(price), image = VALUES(image);
