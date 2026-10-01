package com.zhigou.payment.task;

import com.zhigou.payment.service.PaymentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j @Component @RequiredArgsConstructor
public class ReconciliationTask {
    private final PaymentService paymentService;

    @Scheduled(cron = "0 0 1 * * ?") // 每天凌晨 1 点
    public void run() {
        log.info("T+1 对账任务开始");
        paymentService.reconcile();
        log.info("T+1 对账任务完成");
    }
}