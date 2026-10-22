package com.zhigou.product.service;

import com.zhigou.product.dto.PriceHistoryVO;

import java.util.List;

/** 历史价格（AI 省钱助手 · A 块）：快照采集 + 趋势查询 */
public interface PriceHistoryService {

    /** 每日全量快照：对在售 SKU 记录当日价格（当天已有则跳过） */
    int snapshotAll();

    /** 单 SKU 补点：当天无快照时写入当前价（查询兜底，保证冷启动有基线） */
    void ensureSnapshot(Long skuId);

    /** 查询某 SKU 近 N 天价格趋势（按天去重取最后一条）+ 最低价 + 现价对比 */
    PriceHistoryVO history(Long skuId, int days);
}
