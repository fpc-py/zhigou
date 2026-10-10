CREATE TABLE `warranty_info` (
    `id`             BIGINT AUTO_INCREMENT PRIMARY KEY,
    `order_no`       VARCHAR(64) NOT NULL,
    `sku_id`         VARCHAR(32) NOT NULL,
    `user_id`        BIGINT NOT NULL,
    `product_name`   VARCHAR(128) NOT NULL,
    `purchase_time`  DATETIME NOT NULL,
    `warranty_months` INT NOT NULL DEFAULT 12,
    `expire_time`    DATETIME NOT NULL,
    `create_time`    DATETIME(3) DEFAULT CURRENT_TIMESTAMP(3),
    `update_time`    DATETIME(3) DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    `version` INT DEFAULT 0, `deleted` TINYINT DEFAULT 0,
    INDEX `idx_user_id` (`user_id`), INDEX `idx_order_no` (`order_no`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='商品质保信息（演示底座）';

CREATE TABLE `repair_appointment` (
    `id`              BIGINT AUTO_INCREMENT PRIMARY KEY,
    `appointment_no`  VARCHAR(32) NOT NULL UNIQUE,
    `order_no`        VARCHAR(64) NOT NULL,
    `sku_id`          VARCHAR(32),
    `user_id`         BIGINT NOT NULL,
    `product_name`    VARCHAR(128),
    `fault_desc`      VARCHAR(512) NOT NULL,
    `contact_phone`   VARCHAR(32),
    `appointment_time` DATETIME NOT NULL,
    `status`          VARCHAR(32) NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING/CONFIRMED/DONE/CANCELED',
    `create_time`     DATETIME(3) DEFAULT CURRENT_TIMESTAMP(3),
    `update_time`     DATETIME(3) DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    `version` INT DEFAULT 0, `deleted` TINYINT DEFAULT 0,
    INDEX `idx_user_id` (`user_id`), INDEX `idx_order_no` (`order_no`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='维修预约单';

INSERT INTO `warranty_info` (`order_no`,`sku_id`,`user_id`,`product_name`,`purchase_time`,`warranty_months`,`expire_time`) VALUES
('2107842025067634688','9000000000000000022',2107757313435291648,'无线蓝牙耳机 Pro','2026-01-15 10:00:00',12,'2027-01-15 10:00:00'),
('2107842025067634689','9000000000000000011',2107757313435291648,'智能手环 5','2025-10-25 14:30:00',12,'2026-10-25 14:30:00'),
('2107842025067634690','9000000000000000033',2107757313435291648,'便携榨汁杯','2024-08-05 09:20:00',12,'2025-08-05 09:20:00');
