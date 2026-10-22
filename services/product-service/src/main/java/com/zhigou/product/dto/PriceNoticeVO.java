package com.zhigou.product.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

/** 降价提醒站内信响应（AI 省钱助手 · B 块） */
@Data
@Builder
public class PriceNoticeVO {

    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    @JsonSerialize(using = ToStringSerializer.class)
    private Long skuId;

    private String skuName;

    /** 降价前基准价（分） */
    private Long oldPrice;

    /** 降价后价格（分） */
    private Long newPrice;

    /** 降幅（分） */
    private Long dropAmount;

    private Integer readFlag;
    private LocalDateTime createdAt;
}
