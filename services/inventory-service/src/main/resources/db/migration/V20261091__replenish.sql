CREATE TABLE `replenish_record` (
    `id`          BIGINT AUTO_INCREMENT PRIMARY KEY,
    `sku_id`      BIGINT NOT NULL COMMENT '补货 SKU',
    `before_qty`  INT NOT NULL COMMENT '补货前库存',
    `add_qty`     INT NOT NULL COMMENT '补货数量',
    `after_qty`   INT NOT NULL COMMENT '补货后库存',
    `trigger_type` VARCHAR(16) NOT NULL DEFAULT 'MANUAL' COMMENT 'MANUAL 手动 / AUTO 自动',
    `remark`      VARCHAR(255) DEFAULT NULL,
    `create_time` DATETIME(3) DEFAULT CURRENT_TIMESTAMP(3)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='补货记录';
