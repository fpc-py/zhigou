-- B5 金额加密落库：order_main 金额列 BIGINT → VARCHAR（密文存储，TypeHandler 透明加解密）
-- 存量明文行保持原样（decrypt 对无 ENC: 前缀按明文兼容），增量写入自动加密。
ALTER TABLE `order_main`
    MODIFY COLUMN `total_amount` VARCHAR(256) NOT NULL DEFAULT '0' COMMENT '总金额（分，B5 加密存储）',
    MODIFY COLUMN `pay_amount`   VARCHAR(256) NOT NULL DEFAULT '0' COMMENT '实付（分，B5 加密存储）';
