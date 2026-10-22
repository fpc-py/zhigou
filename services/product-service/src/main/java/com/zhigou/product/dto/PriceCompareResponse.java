package com.zhigou.product.dto;

import lombok.Builder;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Data;

import java.util.List;

/**
 * 跨平台比价响应（P1 六批）。
 */
@Data
@Builder
public class PriceCompareResponse {
    /** SKU ID */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long skuId;
    /** SPU ID */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long spuId;
    /** 商品名（SKU 规格名） */
    private String skuName;
    /** 各渠道报价 */
    private List<CompareOffer> offers;
    /** 最优渠道（总价最低） */
    private String bestSource;
    /** 最优总价（分） */
    private Long bestTotalPrice;
    /** 一句话最优建议（可解释） */
    private String suggestion;
}
