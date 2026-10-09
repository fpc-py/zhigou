package com.zhigou.product.service;

import com.zhigou.product.dto.PriceCompareResponse;

import java.util.List;

/**
 * 跨平台比价服务（P1 六批）。
 */
public interface PriceCompareService {

    /**
     * 对多个 SKU 聚合各渠道报价并计算最优购买方案。
     *
     * @param skuIds SKU ID 列表（1~10 个）
     * @return 每个 SKU 的跨渠道比价结果
     */
    List<PriceCompareResponse> compare(List<Long> skuIds);
}
