package com.zhigou.inventory.mq;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.common.message.MessageQueue;
import org.apache.rocketmq.remoting.protocol.admin.ConsumeStats;
import org.apache.rocketmq.remoting.protocol.admin.OffsetWrapper;
import org.apache.rocketmq.tools.admin.DefaultMQAdminExt;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * B2 RocketMQ 消费积压探针。
 *
 * <p>每 30s 用 DefaultMQAdminExt 拉取各 (topic, consumerGroup) 的消费位点，
 * 积压 = Σ(maxOffset - consumerOffset)，暴露为 Prometheus Gauge
 * {@code rocketmq_consumer_accumulation{topic, group}}——与 monitoring-alerting.md
 * 中 MQBacklog 告警规则（> 1000 触发 P2）直接联动。</p>
 *
 * <p>broker 不可达时降级为 warn 日志（指标保留上次值），不阻断业务。</p>
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "rocketmq.lag.enabled", havingValue = "true", matchIfMissing = true)
public class RocketMqLagProbe {

    @Value("${rocketmq.name-server:localhost:9876}")
    private String nameServer;

    /** targets 格式：topic:consumerGroup，逗号分隔 */
    @Value("${rocketmq.lag.targets:ORDER_CLOSED:inventory-order-closed-group}")
    private String targets;

    private final MeterRegistry meterRegistry;
    private final Map<String, AtomicLong> lagGauges = new ConcurrentHashMap<>();

    public RocketMqLagProbe(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    @PostConstruct
    public void registerGauges() {
        for (String t : targets.split(",")) {
            String[] tg = t.trim().split(":");
            if (tg.length != 2) {
                log.warn("rocketmq.lag.targets 格式错误（应为 topic:group）: {}", t);
                continue;
            }
            String topic = tg[0].trim();
            String group = tg[1].trim();
            AtomicLong value = lagGauges.computeIfAbsent(topic + "|" + group, k -> new AtomicLong(-1));
            Gauge.builder("rocketmq_consumer_accumulation", value, AtomicLong::get)
                    .tag("topic", topic)
                    .tag("group", group)
                    .description("RocketMQ 消费积压（maxOffset - consumerOffset，全队列求和）")
                    .register(meterRegistry);
        }
    }

    @Scheduled(fixedDelayString = "${rocketmq.lag.interval-ms:30000}")
    public void probe() {
        for (String t : targets.split(",")) {
            String[] tg = t.trim().split(":");
            if (tg.length != 2) continue;
            probeOne(tg[0].trim(), tg[1].trim());
        }
    }

    private void probeOne(String topic, String group) {
        DefaultMQAdminExt admin = null;
        try {
            admin = new DefaultMQAdminExt();
            admin.setNamesrvAddr(nameServer);
            admin.start();

            ConsumeStats stats = admin.examineConsumeStats(group);
            Map<MessageQueue, OffsetWrapper> offsetTable = stats.getOffsetTable();
            long lag = 0;
            for (Map.Entry<MessageQueue, OffsetWrapper> e : offsetTable.entrySet()) {
                MessageQueue mq = e.getKey();
                if (!mq.getTopic().equals(topic)) continue;
                OffsetWrapper w = e.getValue();
                lag += Math.max(0, w.getBrokerOffset() - w.getConsumerOffset());
            }
            AtomicLong gauge = lagGauges.get(topic + "|" + group);
            if (gauge != null) gauge.set(lag);
            log.info("MQ 积压采样: topic={} group={} lag={}", topic, group, lag);
        } catch (Exception e) {
            // broker 不可达：保留上次指标值，避免每次采样刷错误日志
            log.warn("MQ 积压采样失败（保留上次值）: topic={} group={} err={}",
                    topic, group,
                    e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage());
        } finally {
            if (admin != null) {
                try { admin.shutdown(); } catch (Exception ignored) { }
            }
        }
    }
}
