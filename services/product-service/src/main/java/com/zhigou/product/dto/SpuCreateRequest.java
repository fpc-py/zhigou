package com.zhigou.product.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import java.util.List;

@Data
public class SpuCreateRequest {
    @NotNull private Long categoryId;
    private Long brandId;
    @NotBlank private String name;
    private String subtitle;
    private String mainImage;
    private String description;
    private List<SkuItem> skus;

    @Data
    public static class SkuItem {
        private String specName;
        private String specValue;
        @NotNull private Long price;
        private Integer stock = 0;
        private String image;
    }
}