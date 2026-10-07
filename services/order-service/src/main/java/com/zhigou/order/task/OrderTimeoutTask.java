package com.zhigou.order.task;

import com.zhigou.order.service.OrderService;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 超时关单定时任务
 *
 * <p>每 5 分钟扫描一次超过阈值仍未支付的 INIT 订单，自动关闭并释放库存。
 * 服务启动时执行一次首扫，避免重启期间积累的超时单长期滞留。</p>
 */
@Slf4j @Component @RequiredArgsConstructor
@EnableScheduling
public class OrderTimeoutTask {

    private final OrderService orderService;

    /** 关单阈值（分钟），默认 15 分钟 */
    @Value("${order.timeout-close-minutes:15}")
    private int timeoutMinutes;

    @PostConstruct
    public void init() {
        log.info("超时关单任务初始化完成，阈值={} 分钟", timeoutMinutes);
        try {
            int closed = orderService.closeExpired(timeoutMinutes);
            if (closed > 0) log.info("启动首扫: 关闭 {} 笔超时订单", closed);
        } catch (Exception e) {
            log.warn("启动首扫异常（下个周期重试）: {}", e.getMessage());
        }
    }

    /** 每 5 分钟扫描一次超时未支付订单 */
    @Scheduled(cron = "0 */5 * * * ?")
    public void run() {
        try {
            int closed = orderService.closeExpired(timeoutMinutes);
            log.info("超时关单扫描完成，本次关闭 {} 笔", closed);
        } catch (Exception e) {
            log.error("超时关单扫描异常: {}", e.getMessage(), e);
        }
    }
}
