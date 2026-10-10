-- 会员订阅（演示：沙箱扣余额，30 天周期；正式接入支付通道后保留字段语义）
ALTER TABLE wallet_account ADD COLUMN member_expire_at DATETIME NULL COMMENT '会员到期时间（订阅后 +30 天，可顺延）';
