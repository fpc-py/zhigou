-- 售后单扩展：售后商品明细（skuId/count，缺省 null=整单退款）+ 退款单号
ALTER TABLE aftersale_order
    ADD COLUMN sku_id      BIGINT       NULL COMMENT '售后商品 SKU（null=整单售后）',
    ADD COLUMN count       INT          NULL COMMENT '售后商品数量',
    ADD COLUMN refund_no   VARCHAR(64)  NULL COMMENT '退款单号（payment-service 返回，幂等键）';
