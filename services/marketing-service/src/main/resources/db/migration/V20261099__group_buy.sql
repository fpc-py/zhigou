-- ============================================================
-- 智购 · 拼团社交购物（社交购物模块）
-- group_buy_activity 团购活动 / group_buy_order 团单 / group_buy_member 团单成员
-- 金额单位：分（与全库一致）
-- ============================================================

CREATE TABLE `group_buy_activity` (
    `id`            BIGINT AUTO_INCREMENT PRIMARY KEY,
    `sku_id`        BIGINT NOT NULL COMMENT '商品 SKU ID',
    `spu_id`        BIGINT NOT NULL COMMENT '商品 SPU ID',
    `title`         VARCHAR(128) NOT NULL COMMENT '活动标题',
    `image_url`     VARCHAR(512) DEFAULT NULL COMMENT '活动图',
    `solo_price`    BIGINT NOT NULL COMMENT '单人购买价(分)',
    `group_price`   BIGINT NOT NULL COMMENT '拼团价(分)',
    `group_size`    INT NOT NULL DEFAULT 2 COMMENT '成团人数',
    `group_stock`   INT NOT NULL DEFAULT 0 COMMENT '可成团份数(库存)',
    `limit_minutes` INT NOT NULL DEFAULT 24 COMMENT '成团限时(小时)',
    `start_time`    DATETIME(3) DEFAULT NULL,
    `end_time`      DATETIME(3) DEFAULT NULL,
    `status`        TINYINT DEFAULT 1 COMMENT '1=进行中 0=下架',
    `create_time`   DATETIME(3) DEFAULT CURRENT_TIMESTAMP(3),
    `update_time`   DATETIME(3) DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    `version` INT DEFAULT 0, `deleted` TINYINT DEFAULT 0
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='拼团活动';

CREATE TABLE `group_buy_order` (
    `id`             BIGINT AUTO_INCREMENT PRIMARY KEY,
    `activity_id`    BIGINT NOT NULL,
    `leader_user_id` BIGINT NOT NULL COMMENT '开团人',
    `target_size`    INT NOT NULL COMMENT '目标成团人数',
    `status`         VARCHAR(32) NOT NULL DEFAULT 'OPEN' COMMENT 'OPEN/SUCCESS/CLOSED',
    `expire_time`    DATETIME(3) DEFAULT NULL COMMENT '成团截止时间',
    `success_time`   DATETIME(3) DEFAULT NULL COMMENT '成团时间',
    `create_time`    DATETIME(3) DEFAULT CURRENT_TIMESTAMP(3),
    `update_time`    DATETIME(3) DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    `version` INT DEFAULT 0, `deleted` TINYINT DEFAULT 0,
    KEY `idx_activity_status` (`activity_id`, `status`),
    KEY `idx_leader` (`leader_user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='拼团团单';

CREATE TABLE `group_buy_member` (
    `id`        BIGINT AUTO_INCREMENT PRIMARY KEY,
    `group_id`  BIGINT NOT NULL,
    `user_id`   BIGINT NOT NULL,
    `is_leader` TINYINT DEFAULT 0,
    `join_time` DATETIME(3) DEFAULT CURRENT_TIMESTAMP(3),
    `create_time` DATETIME(3) DEFAULT CURRENT_TIMESTAMP(3),
    `deleted`   TINYINT DEFAULT 0,
    UNIQUE KEY `uk_group_user` (`group_id`, `user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='团单成员';

-- Seed：3 个进行中拼团活动（商品引用 product-service mock SKU）
INSERT INTO `group_buy_activity`
(sku_id, spu_id, title, image_url, solo_price, group_price, group_size, group_stock, limit_minutes, status) VALUES
(9000000000000000022, 9000000000000000011, '蓝牙耳机 3 人成团 立省 30 元',
 'https://trae-api-cn.mchost.guru/api/ide/v1/text_to_image?prompt=black%20bluetooth%20earbuds%20product%20photo%20white%20background&image_size=square',
 19900, 16900, 3, 10, 24, 1),
(9000000000000000020, 9000000000000000010, '轻量运动鞋 2 人成团 立省 10 元',
 'https://trae-api-cn.mchost.guru/api/ide/v1/text_to_image?prompt=black%20running%20shoes%20product%20photo%20white%20background&image_size=square',
 9900, 8900, 2, 20, 24, 1),
(9000000000000000024, 9000000000000000012, '棉质T恤 3 人成团 立省 10 元',
 'https://trae-api-cn.mchost.guru/api/ide/v1/text_to_image?prompt=black%20cotton%20t%20shirt%20product%20photo%20white%20background&image_size=square',
 4900, 3900, 3, 30, 24, 1);
