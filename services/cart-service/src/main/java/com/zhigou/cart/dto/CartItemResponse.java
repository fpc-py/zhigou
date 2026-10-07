package com.zhigou.cart.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 购物车条目响应。
 * skuId 为 Snowflake ID（19 位），超出 JS Number 安全整数范围，必须序列化为字符串
 * （CLAUDE.md 红线），否则 BFF 聚合商品信息时按损坏的 skuId 匹配不到商品。
 */
@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class CartItemResponse {
    @JsonSerialize(using = ToStringSerializer.class)
    private Long skuId;
    private Integer count;
    private Boolean selected;
    private Long priceAtAdd;
}