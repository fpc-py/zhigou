package com.zhigou.product.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zhigou.product.dto.ReviewStatsVO;
import com.zhigou.product.dto.ReviewSubmitRequest;
import java.util.List;
import com.zhigou.product.dto.ReviewVO;

/** 商品评价服务（P1 四批） */
public interface ProductReviewService {

    /** 发表评价（真实用户，reviewId=Snowflake） */
    Long submit(Long userId, ReviewSubmitRequest request);

    /** 分页查评价列表（可按最低/最高星级过滤，供差评分析） */
    Page<ReviewVO> page(Long spuId, Integer minRating, Integer maxRating, int pageNum, int pageSize);

    /** 评价统计（总数/均分/星级分布/晒图数/差评数/重复内容数） */
    ReviewStatsVO stats(Long spuId);

    /** 全平台低分评价列表（rating <= minRating，按时间倒序，供商家负面预警） */
    List<ReviewVO> negative(int minRating, int limit);

    /** 商家评论管理：待回复评论列表（未出现在 review_reply），附情感 + AI 建议话术（演示口径） */
    java.util.List<java.util.Map<String, Object>> merchantPending(int limit);

    /** 商家评论管理：提交回复（写 review_reply，演示口径） */
    void merchantReply(Long reviewId, String content);
}
