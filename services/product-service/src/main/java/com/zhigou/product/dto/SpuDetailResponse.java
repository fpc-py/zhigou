package com.zhigou.product.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SpuDetailResponse {
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
        private Long skuId;
        private String specName;
        private String specValue;
        private Long price;
        private Integer stock;
        private String image;
    }
}