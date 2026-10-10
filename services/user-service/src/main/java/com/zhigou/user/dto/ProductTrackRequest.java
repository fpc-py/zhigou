package com.zhigou.user.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 收藏 / 浏览 请求体（写时商品信息快照）
 */
@Data
public class ProductTrackRequest {

    /** 商品 SPU ID（Snowflake，转字符串防 JS 精度丢失） */
    @NotNull
    @JsonSerialize(using = ToStringSerializer.class)
    private Long spuId;

    @JsonSerialize(using = ToStringSerializer.class)
    private Long skuId;

    private String spuName;

    /** 价格（分） */
    private Long price;

    private String imageUrl;
}
