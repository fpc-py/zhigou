package com.zhigou.marketing.dto;
import lombok.Data;
import java.util.List;
@Data
public class CalculateRequest {
    private Long userId;
    private List<Item> items;
    private Long couponId;
    @Data public static class Item { private Long skuId; private Integer count; private Long price; }
}