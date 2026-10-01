CREATE TABLE `stock` (
    `id`          BIGINT AUTO_INCREMENT PRIMARY KEY,
    `sku_id`       BIGINT NOT NULL UNIQUE,
    `available`   INT NOT NULL DEFAULT 0 COMMENT '可用库存',
    `locked`      INT NOT NULL DEFAULT 0 COMMENT '锁定库存',
    `version`     INT DEFAULT 0,
    `create_time` DATETIME(3) DEFAULT CURRENT_TIMESTAMP(3),
    `update_time` DATETIME(3) DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='库存表';

INSERT INTO `stock` (sku_id, available) VALUES (1, 100), (2, 10), (3, 1);