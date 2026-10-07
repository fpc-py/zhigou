package com.zhigou.inventory.mq;

import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.zhigou.inventory.service.InventoryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * ORDER_CLOSED 事件消费者（最终一致性兜底通道）
 *
 * <p>关单/取消订单时 order-service 同步调用回滚库存为「主通道」；
 * 本消费者消费 ORDER_CLOSED 消息执行释放为「兜底通道」——当同步调用因网络/服务抖动失败时，
 * 库存释放不丢，由 MQ 可靠投递 + 幂等去重保证最终一致。</p>
 *
 * <p>消息体：{orderId, userId, items:[{skuId, count}]}，释放库存整体按 orderId 幂等。</p>
 */
@Slf4j @Component
@RequiredArgsConstructor
@RocketMQMessageListener(topic = "ORDER_CLOSED", selectorExpression = "*", consumerGroup = "inventory-order-closed-group")
public class OrderClosedListener implements RocketMQListener<String> {

    private final InventoryService inventoryService;

    @Override
    public void onMessage(String payload) {
        try {
            JSONObject body = JSONUtil.parseObj(payload);
            String orderId = body.getStr("orderId");
            JSONArray itemsArr = body.getJSONArray("items");
            if (orderId == null || itemsArr == null || itemsArr.isEmpty()) {
                log.warn("ORDER_CLOSED 消息缺少 orderId/items，忽略: payload={}", payload);
                return;
            }
            List<Map<String, Object>> items = new ArrayList<>(itemsArr.size());
            for (Object o : itemsArr) {
                JSONObject it = (JSONObject) o;
                Map<String, Object> m = new HashMap<>();
                m.put("skuId", it.get("skuId"));
                m.put("count", it.get("count"));
                items.add(m);
            }
            inventoryService.rollbackOrder(orderId, items);
            log.info("ORDER_CLOSED 消费完成: orderId={}, items={}", orderId, items.size());
        } catch (Exception e) {
            // 抛出异常触发 RocketMQ 重试（默认 16 次），最终失败进死信/人工对账
            log.error("ORDER_CLOSED 消费异常: err={}", e.getMessage(), e);
            throw e;
        }
    }
}
