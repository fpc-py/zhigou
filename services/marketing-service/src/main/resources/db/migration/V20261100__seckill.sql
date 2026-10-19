-- ============================================================
-- 智购 · 秒杀活动（限时/限量/防超卖）+ 券叠加互斥
-- seckill_activity：活动主表（sold 原子自增防超卖，行锁保证）
-- 金额单位：分（与全库一致）
-- ============================================================

CREATE TABLE `seckill_activity` (
    `id`            BIGINT AUTO_INCREMENT PRIMARY KEY,
    `sku_id`        BIGINT NOT NULL COMMENT '商品 SKU ID',
    `title`         VARCHAR(128) NOT NULL COMMENT '活动标题',
    `image_url`     VARCHAR(512) DEFAULT NULL COMMENT '活动图',
    `seckill_price` BIGINT NOT NULL COMMENT '秒杀价(分)',
    `origin_price`  BIGINT NOT NULL DEFAULT 0 COMMENT '原价(分)',
    `total`         INT NOT NULL DEFAULT 0 COMMENT '总库存',
    `sold`          INT NOT NULL DEFAULT 0 COMMENT '已售(原子自增防超卖)',
    `per_user_limit` INT NOT NULL DEFAULT 1 COMMENT '每人限购件数',
    `start_time`    DATETIME(3) DEFAULT NULL,
    `end_time`      DATETIME(3) DEFAULT NULL,
    `status`        TINYINT DEFAULT 1 COMMENT '1=上架 0=下架',
    `create_time`   DATETIME(3) DEFAULT CURRENT_TIMESTAMP(3),
    `update_time`   DATETIME(3) DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    `version` INT DEFAULT 0, `deleted` TINYINT DEFAULT 0,
    KEY `idx_sku` (`sku_id`),
    KEY `idx_time` (`start_time`, `end_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='秒杀活动';

-- Seed：2 个进行中秒杀活动 + 1 个未开始（演示口径，引用 product-service mock SKU）
INSERT INTO `seckill_activity`
(sku_id, title, image_url, seckill_price, origin_price, total, sold, per_user_limit, start_time, end_time, status) VALUES
(9000000000000000022, '无线蓝牙耳机 Pro 秒杀 ¥99', 'https://trae-api-cn.mchost.guru/api/ide/v1/text_to_image?prompt=black%20bluetooth%20earbuds%20product%20photo%20white%20background&image_size=square',
 9900, 19900, 50, 8, 1, DATE_SUB(NOW(), INTERVAL 1 HOUR), DATE_ADD(NOW(), INTERVAL 23 HOUR), 1),
(9000000000000000024, '棉质T恤 秒杀 ¥29', 'https://trae-api-cn.mchost.guru/api/ide/v1/text_to_image?prompt=black%20cotton%20t%20shirt%20product%20photo%20white%20background&image_size=square',
 2900, 4900, 100, 20, 2, DATE_SUB(NOW(), INTERVAL 2 HOUR), DATE_ADD(NOW(), INTERVAL 22 HOUR), 1),
(9000000000000000011, '智能手环 5 明日秒杀 ¥89', 'https://trae-api-cn.mchost.guru/api/ide/v1/text_to_image?prompt=black%20smart%20band%20product%20photo%20white%20background&image_size=square',
 8900, 14900, 30, 0, 1, DATE_ADD(NOW(), INTERVAL 24 HOUR), DATE_ADD(NOW(), INTERVAL 48 HOUR), 1);
