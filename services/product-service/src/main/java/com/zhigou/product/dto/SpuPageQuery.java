package com.zhigou.product.dto;

import lombok.Data;

@Data
public class SpuPageQuery {
    private Integer pageNum = 1;
    private Integer pageSize = 20;
    private Long categoryId;
    private Long brandId;
    private String keyword;
}