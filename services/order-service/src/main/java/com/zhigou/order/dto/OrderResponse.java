package com.zhigou.order.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.List;

/**
 * 订单响应。
 * 注意：orderId/userId/skuId 均为 Snowflake ID（19 位），超出 JS Number 安全整数范围，
 * 必须序列化为字符串，否则经 BFF(Node) 转发时会丢精度（CLAUDE.md 红线）。
 */
@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class OrderResponse {
    @JsonSerialize(using = ToStringSerializer.class)
    private Long orderId;
    @JsonSerialize(using = ToStringSerializer.class)
    private Long userId;
    private String orderStatus;
    private Long totalAmount;
    private Long payAmount;
    private List<Item> items;

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class Item {
        @JsonSerialize(using = ToStringSerializer.class)
        private Long skuId;
        private String skuName; private Long price; private Integer count;
    }
}