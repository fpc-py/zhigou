package com.zhigou.order.dto;

import jakarta.validation.Valid;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import java.util.List;

@Data
public class CreateOrderRequest {
    @NotNull private String requestId;
    @NotEmpty @Valid private List<SkuItem> skuItems;
    @JsonSerialize(using = ToStringSerializer.class)
    private Long couponId;

    @Data public static class SkuItem {
        @NotNull private Long skuId;
        @NotNull private Integer count;
    }
}