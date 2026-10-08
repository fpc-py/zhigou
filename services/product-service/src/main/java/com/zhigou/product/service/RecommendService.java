package com.zhigou.product.service;

import com.zhigou.product.dto.RecommendResponse;

/**
 * 个性化推荐服务（P1 五批）。
 * 画像聚合：历史订单（order-service /order/mine）+ 购物车（cart-service /cart/mine）
 * + 评价口碑（product_review 统计），加权评分输出可解释推荐。
 */
public interface RecommendService {

    /**
     * 为用户生成个性化推荐。
     *
     * @param userId 当前用户 ID（服务端注入，不信任外部入参）
     * @param scene  场景：home（首页猜你喜欢）/ cart（购物车凑单）/ detail（详情页相关）
     * @param limit  返回条数上限（默认 6，最大 20）
     * @return 推荐响应（含场景文案与数据来源说明）；无画像数据时降级为口碑榜
     */
    RecommendResponse recommend(Long userId, String scene, int limit);
}
