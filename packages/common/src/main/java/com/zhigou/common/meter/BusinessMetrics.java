package com.zhigou.common.meter;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

/**
 * 智购公共业务指标定义。
 *
 * 各服务注入 MeterRegistry 后使用这些方法记录业务指标。
 * 注册到 Prometheus 后可在 Grafana 中按 application 标签聚合。
 */
@Component
public class BusinessMetrics {

    private final MeterRegistry registry;

    public BusinessMetrics(MeterRegistry registry) {
        this.registry = registry;
    }

    // ==================== 订单 ====================

    public void recordOrderCreated(String source) {
        Counter.builder("order.created")
                .tag("source", source)
                .description("下单量")
                .register(registry)
                .increment();
    }

    public void recordPaymentResult(boolean success) {
        Counter.builder("payment.result")
                .tag("status", success ? "success" : "fail")
                .description("支付结果")
                .register(registry)
                .increment();
    }

    // ==================== 库存 ====================

    public void recordInventoryDeduct(boolean success) {
        Counter.builder("inventory.deduct")
                .tag("status", success ? "success" : "fail")
                .description("库存扣减")
                .register(registry)
                .increment();
    }

    // ==================== AI ====================

    public void recordAiChatCompleted(String source) {
        Counter.builder("ai.chat.completed")
                .tag("source", source)
                .description("AI 对话完成数")
                .register(registry)
                .increment();
    }
}