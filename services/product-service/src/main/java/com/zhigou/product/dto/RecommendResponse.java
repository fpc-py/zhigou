package com.zhigou.product.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.List;

/**
 * 个性化推荐响应（P1 五批）。
 * 推荐项携带可解释理由（reason 标签），数据全部来自内部真实数据：
 * 历史订单（品类/价位偏好）、购物车（当前意向）、评价统计（口碑过滤）。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RecommendResponse {

    /** 场景文案，如「为你推荐 · 根据你的 22 笔订单」 */
    private String sceneText;

    /** 数据来源说明：全部来自真实订单/购物车/评价统计，无编造 */
    private String sourceDesc;

    /** 推荐项列表（按评分降序） */
    private List<RecommendItem> items;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RecommendItem {
        @JsonSerialize(using = ToStringSerializer.class)
        private Long spuId;
        private String name;
        private String mainImage;
        private Long priceMin;
        /** 0~5 综合评分（服务端可复现） */
        private Double score;
        /** 口碑均分（无评价为 null） */
        private Double avgRating;
        /** 评价总数 */
        private Integer reviewCount;
        /** 可解释理由标签 */
        private List<String> reasons;
        /** 特殊标记：如「小众高口碑」/「疑似刷评」 */
        private List<String> tags;
    }
}
