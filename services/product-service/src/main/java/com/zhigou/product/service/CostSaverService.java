package com.zhigou.product.service;

import com.zhigou.product.dto.CostSaverOverviewVO;

/** 省钱助手总览（AI 省钱助手 · C 块）：聚合购物车满减/券/比价/降价 四口径 */
public interface CostSaverService {

    CostSaverOverviewVO overview(Long userId);
}
