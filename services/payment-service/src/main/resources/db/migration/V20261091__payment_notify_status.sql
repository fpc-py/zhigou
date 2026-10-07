-- 支付单通知状态：0=未通知订单服务 1=已通知（幂等标记，供 T+1 对账补偿扫描）
ALTER TABLE payment ADD COLUMN notify_status TINYINT NOT NULL DEFAULT 0 COMMENT '订单联动通知状态 0未通知 1已通知' AFTER status;
