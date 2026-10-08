package com.zhigou.product.dto;

import lombok.Builder;
import lombok.Data;
import java.util.Map;

/** 评价统计（P1 四批：供 AI review_analysis 差评/水军识别） */
@Data
@Builder
public class ReviewStatsVO {
    /** 评价总数 */
    private Long total;
    /** 平均分（1-5，保留 1 位小数） */
    private Double avgRating;
    /** 星级分布 {1: n, 2: n, ..., 5: n} */
    private Map<Integer, Long> ratingDist;
    /** 晒图评价数 */
    private Long imageCount;
    /** 差评数（rating <= 3） */
    private Long lowStarCount;
    /** 重复内容数（内容相同的评价条数，水军特征） */
    private Long duplicateCount;
}
