package com.zhigou.aftersale.dto;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import java.util.List;
@Data
public class ApplyRequest {
    @NotBlank private String orderNo;
    @NotBlank private String type;
    private String reason;
    @NotNull private Long amount;
    /** 售后商品 SKU（可选，null=整单售后） */
    private Long skuId;
    /** 售后商品数量（可选，与 skuId 成对出现） */
    private Integer count;
    private List<String> images;
}