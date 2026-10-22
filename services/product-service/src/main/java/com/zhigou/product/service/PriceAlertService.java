package com.zhigou.product.service;

import com.zhigou.product.dto.PriceAlertVO;
import com.zhigou.product.dto.PriceNoticeVO;

import java.util.List;

/** 降价关注与提醒（AI 省钱助手 · B 块）：关注制，无关注记录=零推送 */
public interface PriceAlertService {

    /** 关注/更新（幂等 upsert；targetPrice 可空=任意降价提醒；watchPrice 重置为当前价） */
    PriceAlertVO watch(Long userId, Long skuId, Long targetPrice);

    /** 取消关注（软删 status=OFF） */
    void unwatch(Long userId, Long skuId);

    /** 我的关注列表（含当前价/是否已降） */
    List<PriceAlertVO> mine(Long userId);

    /** 我的降价提醒（未读/全部） */
    List<PriceNoticeVO> notices(Long userId, boolean unreadOnly);

    /** 标记提醒已读 */
    int markRead(Long userId, Long noticeId);

    /** 每日检测：比对最新快照价 vs watch_price，降价则写 price_notice 并推进 watch_price */
    int checkDrops();
}
