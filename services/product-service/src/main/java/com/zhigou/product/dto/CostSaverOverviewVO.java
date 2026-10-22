package com.zhigou.product.dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * 省钱助手总览响应（AI 省钱助手 · C 块）。
 * 口径独立合并（面试叙事）：满减实省 / 最优券预估 / 比价最优差 / 降价已省 四口径分别计算，
 * totalSaving 只并入确定性口径（满减实省 + 比价最优差 + 降价已省），券为单列预估不重复计入。
 */
@Data
@Builder
public class CostSaverOverviewVO {

    /** 购物车满减实省（分）——deterministic */
    private Long cartDiscountSaving;

    /** 最优可用券面额（分）——预估，单列不计入 total */
    private Long couponBestSaving;

    /** 可用券数量 */
    private Integer usableCouponCount;

    /** 比价最优差（分）：本平台合计 - 跨平台最优合计，正数=按最优渠道买可省 */
    private Long compareSaving;

    /** 最优渠道名（若比价有差） */
    private String bestSource;

    /** 降价已省（分）：未读降价提醒降幅合计 */
    private Long noticeSaving;

    /** 未读降价提醒数 */
    private Integer unreadNotices;

    /** 关注中商品数 */
    private Integer watchCount;

    /** 购物车商品件数 */
    private Integer cartItemCount;

    /** 总节省（分）= cartDiscountSaving + compareSaving + noticeSaving（确定性口径独立合并） */
    private Long totalSaving;

    /** 一句话总结（第一屏可感知） */
    private String summary;

    /** 最近未读降价提醒（最多 3 条） */
    private List<PriceNoticeVO> recentNotices;
}
