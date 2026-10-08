package com.zhigou.order.task;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zhigou.order.entity.Outbox;
import com.zhigou.order.mapper.OutboxMapper;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Outbox 投递任务：本地消息表 → RocketMQ 可靠投递
 *
 * <p>订单创建/关闭等事务只写本地 outbox（status=0），本任务定时扫描并发送到 RocketMQ，
 * 发送成功才置 status=1（投递语义：at-least-once，消费者侧幂等去重）。
 * 失败保留 status=0，下一周期重试，最终保证「本地事务 + 消息投递」最终一致。</p>
 */
@Slf4j @Component
@RequiredArgsConstructor
@EnableScheduling
@Profile("!test")  // test profile 无 RocketMQ，投递任务不加载（Outbox 写入逻辑仍被单测覆盖）
public class OutboxDeliveryTask {

    private final OutboxMapper outboxMapper;
    private final RocketMQTemplate rocketMQTemplate;

    /** 每批投递上限（避免一次扫过多拖垮主线程） */
    private static final int BATCH = 50;

    @PostConstruct
    public void init() {
        log.info("Outbox 投递任务初始化完成");
        try {
            deliverPending();
        } catch (Exception e) {
            log.warn("启动首投异常（下个周期重试）: {}", e.getMessage());
        }
    }

    /** 每 30 秒扫描待投递 outbox */
    @Scheduled(fixedDelay = 30000)
    public void run() {
        try {
            deliverPending();
        } catch (Exception e) {
            log.error("Outbox 投递扫描异常: {}", e.getMessage(), e);
        }
    }

    private void deliverPending() {
        List<Outbox> pending = outboxMapper.selectList(new LambdaQueryWrapper<Outbox>()
                .eq(Outbox::getStatus, 0)
                .orderByAsc(Outbox::getId)
                .last("LIMIT " + BATCH));
        if (pending.isEmpty()) return;

        int sent = 0, failed = 0;
        for (Outbox box : pending) {
            try {
                // RocketMQ 主题:标签 格式 topic:tag；发送失败抛异常保留 status=0 待重投
                String destination = box.getTopic() + ":" + (box.getTag() == null ? "*" : box.getTag());
                rocketMQTemplate.syncSend(destination, box.getPayload());
                box.setStatus(1);
                outboxMapper.updateById(box);
                sent++;
            } catch (Exception e) {
                failed++;
                log.warn("Outbox 投递失败（下周期重试）: id={}, topic={}, err={}",
                        box.getId(), box.getTopic(), e.getMessage());
            }
        }
        if (sent > 0) log.info("Outbox 投递: 成功 {} 条, 失败 {} 条", sent, failed);
    }
}
