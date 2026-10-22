package com.zhigou.product.task;

import com.zhigou.product.service.PriceAlertService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** 每日降价检测任务（AI 省钱助手 · B 块）。03:10 执行（在 02:10 价格快照之后）。 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PriceAlertCheckTask {

    private final PriceAlertService priceAlertService;

    @Scheduled(cron = "0 10 3 * * *")
    public void dailyCheck() {
        int n = priceAlertService.checkDrops();
        log.info("每日降价检测任务完成，命中 {} 条提醒", n);
    }
}
