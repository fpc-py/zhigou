-- ============================================================
-- 智购 · 部分发货（订单分批发货）
-- order_shipment：发货明细（每批一条），全量发完订单状态 SHIPPING→SHIPPED
-- ============================================================

CREATE TABLE `order_shipment` (
    `id`          BIGINT AUTO_INCREMENT PRIMARY KEY,
    `order_id`    BIGINT NOT NULL COMMENT '订单ID(order_main.order_id)',
    `sku_id`      BIGINT NOT NULL COMMENT '商品SKU',
    `count`       INT NOT NULL COMMENT '本批发货数量',
    `tracking_no` VARCHAR(64) DEFAULT NULL COMMENT '物流单号',
    `ship_time`   DATETIME(3) DEFAULT CURRENT_TIMESTAMP(3),
    `create_time` DATETIME(3) DEFAULT CURRENT_TIMESTAMP(3),
    `deleted`     TINYINT DEFAULT 0,
    KEY `idx_order` (`order_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='订单发货明细';
