package com.zhigou.product.dto;

import lombok.Data;

@Data
public class SpuPageQuery {
    private Integer pageNum = 1;
    private Integer pageSize = 20;
    private Long categoryId;
    private Long brandId;
    private String keyword;
    /** 商家ID（多商家隔离：商家管理端按归属过滤；为空=消费者端跨商家浏览） */
    private Long merchantId;
}