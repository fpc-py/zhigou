package com.zhigou.order.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.List;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class OrderResponse {
    private Long orderId;
    private String orderStatus;
    private Long totalAmount;
    private Long payAmount;
    private List<Item> items;

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class Item {
        private Long skuId; private String skuName; private Long price; private Integer count;
    }
}