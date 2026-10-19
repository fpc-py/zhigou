package com.zhigou.marketing.service;

import com.zhigou.marketing.entity.SeckillActivity;

import java.util.List;
import java.util.Map;

public interface SeckillService {
    /** 进行中秒杀活动列表 */
    List<SeckillActivity> listActive();

    /** 活动详情 */
    SeckillActivity detail(Long id);

    /**
     * 秒杀预扣：校验时间/余量/限购，原子扣减防超卖。
     * @return { activity, boughtCount, remainBuyable, snapshot } 快照含秒杀价
     */
    Map<String, Object> buy(Long userId, Long activityId, Integer count);

    /** 购买失败释放预扣 */
    void release(Long activityId, Integer count);
}
