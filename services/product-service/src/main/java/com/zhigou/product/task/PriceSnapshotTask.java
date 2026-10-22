package com.zhigou.product.task;

import com.zhigou.product.service.PriceHistoryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** 历史价格每日全量快照任务（AI 省钱助手 · A 块）。每日 02:10 执行，避开交易高峰。 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PriceSnapshotTask {

    private final PriceHistoryService priceHistoryService;

    @Scheduled(cron = "0 10 2 * * *")
    public void dailySnapshot() {
        int n = priceHistoryService.snapshotAll();
        log.info("历史价格每日快照任务完成，写入 {} 条", n);
    }
}
