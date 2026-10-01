package com.zhigou.cart.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class CartItemResponse {
    private Long skuId;
    private Integer count;
    private Boolean selected;
    private Long priceAtAdd;
}