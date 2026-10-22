package com.zhigou.product.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

/** 关注项响应（AI 省钱助手 · B 块） */
@Data
@Builder
public class PriceAlertVO {

    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    @JsonSerialize(using = ToStringSerializer.class)
    private Long skuId;

    private String skuName;

    /** 关注时价格（分） */
    private Long watchPrice;

    /** 当前价格（分） */
    private Long currentPrice;

    /** 期望价（分），NULL=任意降价 */
    private Long targetPrice;

    /** 是否已降价（currentPrice < watchPrice） */
    private Boolean dropped;

    /** 已降金额（分），未降为 0 */
    private Long dropAmount;

    private String status;
    private LocalDateTime updatedAt;
}
