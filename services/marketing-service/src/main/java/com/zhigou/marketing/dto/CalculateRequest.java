package com.zhigou.marketing.dto;
import lombok.Data;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import java.util.List;
@Data
public class CalculateRequest {
    private Long userId;
    private List<Item> items;
    @JsonSerialize(using = ToStringSerializer.class)
    private Long couponId;
    @Data public static class Item {
        @JsonSerialize(using = ToStringSerializer.class)
        private Long skuId;
        private Integer count;
        private Long price;
        /** true=秒杀价商品（互斥规则：不参与满减/券，直接按秒杀价计价） */
        private Boolean seckill;
    }
}