CREATE TABLE `freight_template` (
    `id`                 BIGINT AUTO_INCREMENT PRIMARY KEY,
    `name`               VARCHAR(128) NOT NULL,
    `first_weight_g`     INT NOT NULL COMMENT '首重(克)',
    `first_fee`          BIGINT NOT NULL COMMENT '首重费(分)',
    `continued_weight_g` INT NOT NULL COMMENT '续重(克)',
    `continued_fee`      BIGINT NOT NULL COMMENT '续重费(分)',
    `free_threshold_amount` BIGINT DEFAULT NULL COMMENT '满额包邮(分)',
    `region_json`        VARCHAR(1024) DEFAULT NULL,
    `create_time` DATETIME(3) DEFAULT CURRENT_TIMESTAMP(3),
    `update_time` DATETIME(3) DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    `version` INT DEFAULT 0, `deleted` TINYINT DEFAULT 0
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='运费模板';

CREATE TABLE `shipment` (
    `id`           BIGINT AUTO_INCREMENT PRIMARY KEY,
    `shipment_no`  VARCHAR(32) NOT NULL UNIQUE,
    `order_no`     VARCHAR(64) NOT NULL,
    `user_id`       BIGINT NOT NULL,
    `sender_addr`  VARCHAR(512) DEFAULT NULL,
    `receiver_addr` TEXT DEFAULT NULL COMMENT '收货地址JSON',
    `weight_g`     INT NOT NULL DEFAULT 0,
    `freight_fee`  BIGINT NOT NULL DEFAULT 0,
    `carrier`      VARCHAR(32) DEFAULT 'SELF',
    `status`       VARCHAR(32) NOT NULL DEFAULT 'CREATED',
    `shipped_at`   DATETIME(3) DEFAULT NULL,
    `delivered_at` DATETIME(3) DEFAULT NULL,
    `create_time`  DATETIME(3) DEFAULT CURRENT_TIMESTAMP(3),
    `update_time`  DATETIME(3) DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    `version` INT DEFAULT 0, `deleted` TINYINT DEFAULT 0,
    INDEX `idx_order_no` (`order_no`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='运单';

CREATE TABLE `track_event` (
    `id`          BIGINT AUTO_INCREMENT PRIMARY KEY,
    `shipment_no` VARCHAR(32) NOT NULL,
    `node_time`   DATETIME(3) NOT NULL,
    `node_name`   VARCHAR(64) NOT NULL,
    `description` VARCHAR(256),
    `create_time` DATETIME(3) DEFAULT CURRENT_TIMESTAMP(3),
    INDEX `idx_shipment_no` (`shipment_no`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='轨迹事件';

INSERT INTO `freight_template` (id,name,first_weight_g,first_fee,continued_weight_g,continued_fee,free_threshold_amount) VALUES
(1, '默认运费模板', 1000, 1000, 1000, 200, 10000);