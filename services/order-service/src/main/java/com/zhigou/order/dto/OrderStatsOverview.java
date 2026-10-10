package com.zhigou.order.dto;

import lombok.Data;

import java.util.List;
import java.util.Map;

/** 平台经营概览（商家视角 = 单商家市场聚合，演示口径） */
@Data
public class OrderStatsOverview {
    /** 订单总数 */
    private Long totalOrders;
    /** 累计销售额（分）：PAID/SHIPPED/COMPLETED 的实付合计 */
    private Long totalSalesFen;
    /** 今日订单数 */
    private Long todayOrders;
    /** 今日销售额（分） */
    private Long todaySalesFen;
    /** 订单状态分布 */
    private Map<String, Long> statusDist;
    /** 热销 SPU Top5 */
    private List<HotSpu> hotSpus;
    /** 待处理售后（REFUNDING 订单数，演示口径） */
    private Long pendingAfterSale;
    /** 生成时间 */
    private String generatedAt;

    @Data
    public static class HotSpu {
        private Long spuId;
        private String spuName;
        private Long soldCount;
        private Long salesFen;
    }
}
