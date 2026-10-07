-- 退款单表（沙箱退款资金流）
-- 说明：沙箱环境退款即时成功；生产替换为微信/支付宝 refund SDK 回调后置为 REFUNDING 再异步置 SUCCESS
CREATE TABLE IF NOT EXISTS payment_refund (
    id           BIGINT       AUTO_INCREMENT PRIMARY KEY COMMENT '主键',
    refund_no    VARCHAR(64)  NOT NULL COMMENT '退款单号（幂等键）',
    payment_no   VARCHAR(64)  NOT NULL COMMENT '原支付单号',
    order_no     VARCHAR(64)  NOT NULL COMMENT '业务订单号',
    user_id      BIGINT       NOT NULL COMMENT '用户ID',
    amount       BIGINT       NOT NULL COMMENT '退款金额（分）',
    status       VARCHAR(32)  NOT NULL DEFAULT 'REFUNDING' COMMENT '状态: REFUNDING 处理中 / SUCCESS 成功 / FAILED 失败',
    reason       VARCHAR(255) NULL COMMENT '退款原因',
    refunded_at  DATETIME     NULL COMMENT '退款成功时间',
    create_time  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    version      INT          NOT NULL DEFAULT 0 COMMENT '乐观锁',
    deleted      INT          NOT NULL DEFAULT 0 COMMENT '逻辑删除',
    UNIQUE KEY uk_refund_no (refund_no),
    KEY idx_payment_no (payment_no),
    KEY idx_order_no (order_no)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '退款单';
