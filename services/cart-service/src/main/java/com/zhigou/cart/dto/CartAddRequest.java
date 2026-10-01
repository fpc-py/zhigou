package com.zhigou.cart.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class CartAddRequest {
    @NotNull private Long skuId;
    @Min(1) private Integer count = 1;
}