CREATE TABLE `fulfillment_action` (
    `id`          BIGINT AUTO_INCREMENT PRIMARY KEY,
    `order_id`    BIGINT NOT NULL COMMENT '异常订单 ID',
    `sku_id`      BIGINT DEFAULT NULL COMMENT '关联 SKU（可为空）',
    `action`      VARCHAR(16) NOT NULL COMMENT 'SPLIT 拆分发货 / DELAY 延期发货 / OFF_SHELF 下架停单 / REPLENISH 补货',
    `reason`      VARCHAR(255) DEFAULT NULL COMMENT '处理原因（缺货/延误等）',
    `status`      VARCHAR(16) NOT NULL DEFAULT 'DONE' COMMENT 'PENDING 待执行 / DONE 已执行',
    `create_time` DATETIME(3) DEFAULT CURRENT_TIMESTAMP(3)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='履约异常订单处理记录';
