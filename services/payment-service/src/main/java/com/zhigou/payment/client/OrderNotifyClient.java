package com.zhigou.payment.client;

import cn.hutool.http.HttpUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 订单服务联动客户端：支付成功后通知 order-service 将订单置为 PAID。
 * 说明：失败不阻断支付主流程（支付单已 SUCCESS），重试 3 次后仅记录日志，
 * 由 T+1 对账/补偿任务兜底（见 docs/真实支付接入指南.md 3.4 方案 A）。
 */
@Slf4j @Component
public class OrderNotifyClient {

    @Value("${payment.order-service-url:http://localhost:8085}") private String orderServiceUrl;

    /**
     * 通知订单已支付。HTTP 失败重试 3 次（指数退避）。
     *
     * @return true=通知成功（或幂等成功）；false=最终失败，交由 T+1 对账补偿
     */
    public boolean notifyPaid(String orderNo) {
        String url = orderServiceUrl + "/order/payCallback/" + orderNo;
        for (int i = 1; i <= 3; i++) {
            try {
                String body = HttpUtil.post(url, "");
                log.info("通知订单支付成功: orderNo={}, resp={}", orderNo, body);
                return true;
            } catch (Exception e) {
                log.warn("通知订单支付第 {} 次失败: orderNo={}, err={}", i, orderNo, e.getMessage());
                try { Thread.sleep(500L * i); } catch (InterruptedException ie) { Thread.currentThread().interrupt(); return false; }
            }
        }
        log.error("通知订单支付最终失败，交由对账补偿: orderNo={}", orderNo);
        return false;
    }
}
