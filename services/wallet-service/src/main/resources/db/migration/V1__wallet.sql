-- 会员钱包（演示：沙箱充值入账，不接真实支付通道；金额单位分）
CREATE TABLE IF NOT EXISTS wallet_account (
    id                 BIGINT       NOT NULL PRIMARY KEY,
    user_id            BIGINT       NOT NULL,
    balance_fen        BIGINT       DEFAULT 0 COMMENT '余额（分）',
    total_recharge_fen BIGINT       DEFAULT 0 COMMENT '累计充值（分）',
    total_consume_fen  BIGINT       DEFAULT 0 COMMENT '累计消费（分）',
    points             INT          DEFAULT 0 COMMENT '当前积分',
    total_points       INT          DEFAULT 0 COMMENT '累计获取积分',
    member_level       VARCHAR(16)  DEFAULT 'FREE' COMMENT 'FREE/ADVANCED/FLAGSHIP',
    status             TINYINT      DEFAULT 1,
    deleted            TINYINT      DEFAULT 0,
    created_at         DATETIME     DEFAULT CURRENT_TIMESTAMP,
    updated_at         DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_user (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='钱包账户（演示）';

CREATE TABLE IF NOT EXISTS wallet_transaction (
    id                BIGINT       NOT NULL PRIMARY KEY,
    user_id           BIGINT       NOT NULL,
    type              VARCHAR(16)  NOT NULL COMMENT 'RECHARGE/ CONSUME / REFUND',
    amount_fen        BIGINT       NOT NULL,
    balance_after_fen BIGINT       NOT NULL,
    biz_no            VARCHAR(64)  NOT NULL COMMENT '业务单号（幂等）',
    remark            VARCHAR(255) DEFAULT '',
    created_at        DATETIME     DEFAULT CURRENT_TIMESTAMP,
    KEY idx_user (user_id, created_at),
    UNIQUE KEY uk_biz (user_id, biz_no)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='钱包流水（演示）';

-- Seed：用户 13800138001 演示账户（余额 500 元 = 50000 分、积分 320、高级会员）
INSERT INTO wallet_account (id, user_id, balance_fen, total_recharge_fen, total_consume_fen, points, total_points, member_level) VALUES
(9000000000000001001, 2107757313435291648, 50000, 80000, 30000, 320, 800, 'ADVANCED');

INSERT INTO wallet_transaction (id, user_id, type, amount_fen, balance_after_fen, biz_no, remark, created_at) VALUES
(9000000000000001101, 2107757313435291648, 'RECHARGE', 30000, 30000, 'R202610010001', '沙箱充值，赠 300 积分', '2026-10-01 10:00:00'),
(9000000000000001102, 2107757313435291648, 'RECHARGE', 50000, 80000, 'R202610020001', '沙箱充值，赠 500 积分', '2026-10-02 20:00:00'),
(9000000000000001103, 2107757313435291648, 'CONSUME',  30000, 50000, 'O202610030001', '订单 20261003 支付', '2026-10-03 15:30:00'),
(9000000000000001104, 2107757313435291648, 'REFUND',    5000, 55000, 'RFD202610030001', '订单 20261003 部分退款', '2026-10-03 16:00:00');
