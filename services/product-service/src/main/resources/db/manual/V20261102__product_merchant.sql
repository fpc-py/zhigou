-- ============================================================
-- 智购 · product-service：多商家隔离（merchant_id）
-- - product_spu 新增 merchant_id：商品归属商家（多租户数据隔离）
-- - 现有商品（商家A 演示）回填 merchant_id=1
-- - 新增商家B 种子商品：智能手表 / 休闲帆布鞋（merchant_id=2）
-- ============================================================

ALTER TABLE `product_spu`
    ADD COLUMN `merchant_id` BIGINT DEFAULT NULL
        COMMENT '所属商家ID（多商家隔离）' AFTER `brand_id`;

UPDATE `product_spu` SET `merchant_id` = 1 WHERE `merchant_id` IS NULL;

INSERT INTO `product_spu` (spu_id, category_id, brand_id, merchant_id, name, subtitle, main_image, description, status, price_min, price_max, sales_volume) VALUES
(9000000000000000013, 2, 1, 2, '商家B-智能手表', '独立商家B：健康监测',
 'https://trae-api-cn.mchost.guru/api/ide/v1/text_to_image?prompt=smart%20watch%20product%20photo%20white%20background&image_size=square',
 '商家B 上架的智能手表（多商家隔离验收商品）', 1, 59900, 89900, 0),
(9000000000000000014, 1, 1, 2, '商家B-休闲帆布鞋', '独立商家B：街头潮流',
 'https://trae-api-cn.mchost.guru/api/ide/v1/text_to_image?prompt=canvas%20shoes%20product%20photo%20white%20background&image_size=square',
 '商家B 上架的休闲帆布鞋（多商家隔离验收商品）', 1, 12900, 16900, 0)
ON DUPLICATE KEY UPDATE merchant_id = VALUES(merchant_id);

INSERT INTO `product_sku` (sku_id, spu_id, spec_name, spec_value, price, stock, image) VALUES
(9000000000000000026, 9000000000000000013, '颜色', '黑色', 59900, 40, 'https://trae-api-cn.mchost.guru/api/ide/v1/text_to_image?prompt=black%20smart%20watch%20product%20photo%20white%20background&image_size=square'),
(9000000000000000027, 9000000000000000013, '颜色', '银色', 89900, 30, 'https://trae-api-cn.mchost.guru/api/ide/v1/text_to_image?prompt=silver%20smart%20watch%20product%20photo%20white%20background&image_size=square'),
(9000000000000000028, 9000000000000000014, '颜色', '米白', 12900, 60, 'https://trae-api-cn.mchost.guru/api/ide/v1/text_to_image?prompt=white%20canvas%20shoes%20product%20photo%20white%20background&image_size=square'),
(9000000000000000029, 9000000000000000014, '颜色', '藏青', 16900, 45, 'https://trae-api-cn.mchost.guru/api/ide/v1/text_to_image?prompt=navy%20canvas%20shoes%20product%20photo%20white%20background&image_size=square')
ON DUPLICATE KEY UPDATE price = VALUES(price), image = VALUES(image);
