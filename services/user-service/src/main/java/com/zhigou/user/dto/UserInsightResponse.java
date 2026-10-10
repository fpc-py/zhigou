package com.zhigou.user.dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * 用户画像洞察（演示口径：实时聚合收藏 + 浏览 + 资料，供 AI Agent 调用）
 */
@Data
@Builder
public class UserInsightResponse {

    /** 收藏数 */
    private Long favoriteCount;

    /** 浏览商品数（去重 SPU 数） */
    private Long browseCount;

    /** 累计浏览次数（含重复浏览） */
    private Long browseTotal;

    /** 偏好品类 Top5（按收藏+浏览聚合，词表归类） */
    private List<String> topCategories;

    /** 常购价位带（价格中位所在分桶） */
    private String priceBand;

    /** 最近浏览 Top5 */
    private List<ProductTrackItem> recentBrowse;

    /** 说明：画像为实时聚合口径，量大后可落表离线化 */
    private String note;
}
