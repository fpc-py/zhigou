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
}
