package com.zhigou.product.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SpuDetailResponse {
    /** Snowflake ID 超出 JS 安全整数范围，序列化为字符串避免前端精度丢失 */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long spuId;
    private String name;
    private String subtitle;
    private String mainImage;
    private String description;
    private Integer status;
    private Long priceMin;
    private Long priceMax;
    private Integer salesVolume;
    private Long categoryId;
    private Long brandId;
    private List<SkuItem> skus;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SkuItem {
        /** Snowflake ID 序列化为字符串避免前端精度丢失 */
        @JsonSerialize(using = ToStringSerializer.class)
        private Long skuId;
        private String specName;
        private String specValue;
        private Long price;
        private Integer stock;
        private String image;
    }
}