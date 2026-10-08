package com.zhigou.product.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/** 发表评价请求（P1 四批） */
@Data
public class ReviewSubmitRequest {

    @NotNull(message = "spuId 不能为空")
    private Long spuId;

    @NotNull(message = "评分不能为空")
    @Min(value = 1, message = "评分范围 1-5")
    @Max(value = 5, message = "评分范围 1-5")
    private Integer rating;

    @NotBlank(message = "评价内容不能为空")
    @Size(max = 1000, message = "评价内容最长 1000 字")
    private String content;

    /** 晒图 URL，逗号分隔，可空 */
    private String images;
}
