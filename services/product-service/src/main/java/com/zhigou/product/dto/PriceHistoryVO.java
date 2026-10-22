package com.zhigou.product.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/** 历史价格查询响应（AI 省钱助手 · A 块） */
@Data
@Builder
public class PriceHistoryVO {

    @JsonSerialize(using = ToStringSerializer.class)
    private Long skuId;

    private String skuName;

    /** 当前售价（分） */
    private Long currentPrice;

    /** 近 N 天最低价（分） */
    private Long lowestPrice;

    /** 最低价日期 */
    private LocalDate lowestDate;

    /** 相对最低价：当前价 - 最低价（分），=0 表示当前即最低 */
    private Long diffFromLowest;

    /** 每日价格序列：date -> price（分） */
    private Map<LocalDate, Long> dailyPrices;

    /** 采样天数（实际有快照的天数） */
    private int sampledDays;

    /** 提示文案（面试叙事：口径明确） */
    private String summary;
}
