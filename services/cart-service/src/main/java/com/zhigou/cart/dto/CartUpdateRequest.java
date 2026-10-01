package com.zhigou.cart.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class CartUpdateRequest {
    @NotNull private Long skuId;
    private Integer count;
    private Boolean selected;
}