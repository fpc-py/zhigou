CREATE TABLE `dispatch_action` (
    `id`          BIGINT AUTO_INCREMENT PRIMARY KEY,
    `shipment_no` VARCHAR(32) NOT NULL COMMENT '运单号',
    `action`      VARCHAR(16) NOT NULL COMMENT 'URGE 催件 / REDELIVER 重新派送 / SELF_PICKUP 自提 / CHANGE_ADDRESS 改址 / RETURN 退换货',
    `reason`      VARCHAR(255) DEFAULT NULL COMMENT '调度原因（延误/改址等）',
    `status`      VARCHAR(16) NOT NULL DEFAULT 'DONE' COMMENT 'PENDING 待执行 / DONE 已执行',
    `create_time` DATETIME(3) DEFAULT CURRENT_TIMESTAMP(3)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='物流一键调度记录（演示口径）';
