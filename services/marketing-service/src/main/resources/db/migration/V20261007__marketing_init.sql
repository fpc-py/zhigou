CREATE TABLE `coupon_template` (
    `id`              BIGINT AUTO_INCREMENT PRIMARY KEY,
    `name`            VARCHAR(128) NOT NULL,
    `type`            VARCHAR(32) NOT NULL COMMENT 'FULL_REDUCE/DISCOUNT/CASH',
    `threshold_amount` BIGINT DEFAULT 0 COMMENT '门槛分',
    `discount_value`  BIGINT NOT NULL COMMENT '优惠值(分/百分比)',
    `total_count`     INT DEFAULT 0,
    `per_user_limit`  INT DEFAULT 1,
    `valid_start`     DATETIME(3) DEFAULT NULL,
    `valid_end`       DATETIME(3) DEFAULT NULL,
    `status`          TINYINT DEFAULT 1,
    `create_time`     DATETIME(3) DEFAULT CURRENT_TIMESTAMP(3),
    `update_time`     DATETIME(3) DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    `version` INT DEFAULT 0, `deleted` TINYINT DEFAULT 0
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='优惠券模板';

CREATE TABLE `user_coupon` (
    `id`                BIGINT AUTO_INCREMENT PRIMARY KEY,
    `user_id`            BIGINT NOT NULL,
    `coupon_template_id` BIGINT NOT NULL,
    `status`            VARCHAR(32) NOT NULL DEFAULT 'UNUSED' COMMENT 'UNUSED/FROZEN/USED/EXPIRED',
    `source_order_id`   BIGINT DEFAULT NULL,
    `locked_at`         DATETIME(3) DEFAULT NULL,
    `used_at`           DATETIME(3) DEFAULT NULL,
    `create_time`       DATETIME(3) DEFAULT CURRENT_TIMESTAMP(3),
    `update_time`       DATETIME(3) DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    `version` INT DEFAULT 0, `deleted` TINYINT DEFAULT 0
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户优惠券';

CREATE TABLE `promotion_rule` (
    `id`           BIGINT AUTO_INCREMENT PRIMARY KEY,
    `name`         VARCHAR(128) NOT NULL,
    `rule_content` TEXT NOT NULL COMMENT 'QLExpress 脚本',
    `priority`     INT DEFAULT 0,
    `status`       TINYINT DEFAULT 1,
    `start_time`   DATETIME(3) DEFAULT NULL,
    `end_time`     DATETIME(3) DEFAULT NULL,
    `create_time`  DATETIME(3) DEFAULT CURRENT_TIMESTAMP(3),
    `update_time`  DATETIME(3) DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    `version` INT DEFAULT 0, `deleted` TINYINT DEFAULT 0
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='促销规则';

CREATE TABLE `discount_snapshot` (
    `id`              BIGINT AUTO_INCREMENT PRIMARY KEY,
    `order_no`        VARCHAR(64) NOT NULL,
    `total_amount`    BIGINT NOT NULL,
    `discount_amount` BIGINT NOT NULL DEFAULT 0,
    `coupon_id`       BIGINT DEFAULT NULL,
    `rule_ids`        VARCHAR(512) DEFAULT NULL COMMENT 'JSON array of rule IDs',
    `final_amount`    BIGINT NOT NULL,
    `create_time`     DATETIME(3) DEFAULT CURRENT_TIMESTAMP(3)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='优惠快照';

-- Seed: 满 200 减 30, 8 折券, 直减 5 元
INSERT INTO `coupon_template` (id,name,type,threshold_amount,discount_value,total_count,per_user_limit) VALUES
(1, '满200减30', 'FULL_REDUCE', 20000, 3000, 100, 3),
(2, '8折券', 'DISCOUNT', 0, 80, 50, 1),
(3, '直减5元', 'CASH', 0, 500, 200, 5);

INSERT INTO `promotion_rule` (id,name,rule_content,priority) VALUES
(1, '满200减30', 'if totalAmount >= 20000 then return 3000; else return 0; end', 1);