CREATE TABLE `category` (
    `id`          BIGINT AUTO_INCREMENT PRIMARY KEY,
    `parent_id`   BIGINT DEFAULT 0     COMMENT '父分类ID，0 为根',
    `name`        VARCHAR(64) NOT NULL,
    `level`       INT DEFAULT 1        COMMENT '层级 1/2/3',
    `sort_order`  INT DEFAULT 0,
    `status`      TINYINT DEFAULT 1    COMMENT '0禁用 1启用',
    `create_time` DATETIME(3) DEFAULT CURRENT_TIMESTAMP(3),
    `update_time` DATETIME(3) DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    `version`     INT DEFAULT 0,
    `deleted`     TINYINT DEFAULT 0
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='商品分类';

CREATE TABLE `brand` (
    `id`          BIGINT AUTO_INCREMENT PRIMARY KEY,
    `name`        VARCHAR(100) NOT NULL,
    `logo_url`    VARCHAR(512),
    `description` VARCHAR(1024),
    `status`      TINYINT DEFAULT 1    COMMENT '0禁用 1启用',
    `create_time` DATETIME(3) DEFAULT CURRENT_TIMESTAMP(3),
    `update_time` DATETIME(3) DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    `version`     INT DEFAULT 0,
    `deleted`     TINYINT DEFAULT 0
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='品牌';

CREATE TABLE `product_spu` (
    `id`           BIGINT AUTO_INCREMENT PRIMARY KEY,
    `spu_id`       BIGINT NOT NULL UNIQUE COMMENT '业务 SPU ID',
    `category_id`  BIGINT NOT NULL,
    `brand_id`     BIGINT DEFAULT NULL,
    `name`         VARCHAR(256) NOT NULL,
    `subtitle`     VARCHAR(512),
    `main_image`   VARCHAR(512),
    `description`  TEXT             COMMENT '图文详情（HTML/Markdown）',
    `status`       TINYINT DEFAULT 1 COMMENT '0下架 1上架',
    `price_min`    BIGINT DEFAULT 0  COMMENT '最低价（分）',
    `price_max`    BIGINT DEFAULT 0  COMMENT '最高价（分）',
    `sales_volume` INT DEFAULT 0,
    `create_time`  DATETIME(3) DEFAULT CURRENT_TIMESTAMP(3),
    `update_time`  DATETIME(3) DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    `version`      INT DEFAULT 0,
    `deleted`      TINYINT DEFAULT 0,
    INDEX `idx_category_status` (`category_id`, `status`),
    INDEX `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='SPU';

CREATE TABLE `product_sku` (
    `id`          BIGINT AUTO_INCREMENT PRIMARY KEY,
    `sku_id`      BIGINT NOT NULL UNIQUE COMMENT '业务 SKU ID',
    `spu_id`      BIGINT NOT NULL,
    `spec_name`   VARCHAR(128)   COMMENT '规格名（颜色/尺码）',
    `spec_value`  VARCHAR(128)   COMMENT '规格值（红色/XL）',
    `price`       BIGINT NOT NULL COMMENT '单价（分）',
    `stock`       INT DEFAULT 0,
    `image`       VARCHAR(512),
    `create_time` DATETIME(3) DEFAULT CURRENT_TIMESTAMP(3),
    `update_time` DATETIME(3) DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    `version`     INT DEFAULT 0,
    `deleted`     TINYINT DEFAULT 0,
    INDEX `idx_spu_id` (`spu_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='SKU';

-- 种子数据
INSERT INTO `category` (id, parent_id, name, level, sort_order) VALUES
(1, 0, '服装', 1, 1), (2, 0, '数码', 1, 2), (3, 1, '男装', 2, 1), (4, 1, '女装', 2, 2);
INSERT INTO `brand` (id, name, logo_url) VALUES (1, '测试品牌', 'https://example.com/logo.png');