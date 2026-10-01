package com.zhigou.order.service.impl;

import cn.hutool.core.util.IdUtil;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zhigou.common.BizException;
import com.zhigou.order.dto.CreateOrderRequest;
import com.zhigou.order.dto.OrderResponse;
import com.zhigou.order.entity.OrderItem;
import com.zhigou.order.entity.OrderMain;
import com.zhigou.order.entity.Outbox;
import com.zhigou.order.mapper.OrderItemMapper;
import com.zhigou.order.mapper.OrderMainMapper;
import com.zhigou.order.mapper.OutboxMapper;
import com.zhigou.order.service.OrderService;
import com.zhigou.order.state.OrderState;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j @Service @RequiredArgsConstructor
public class OrderServiceImpl implements OrderService {

    private final OrderMainMapper orderMapper;
    private final OrderItemMapper itemMapper;
    private final OutboxMapper outboxMapper;

    @Override @Transactional
    public OrderResponse create(Long userId, CreateOrderRequest req) {
        // 1. 幂等检查
        OrderMain existing = orderMapper.selectOne(new LambdaQueryWrapper<OrderMain>()
                .eq(OrderMain::getUserId, userId).eq(OrderMain::getRequestId, req.getRequestId()));
        if (existing != null) {
            log.info("重复下单: requestId={}", req.getRequestId());
            return buildResponse(existing);
        }

        // 2. 创建订单
        long orderId = IdUtil.getSnowflakeNextId();
        long totalAmount = 0;
        List<OrderItem> items = new ArrayList<>();

        for (CreateOrderRequest.SkuItem si : req.getSkuItems()) {
            OrderItem item = new OrderItem();
            item.setOrderId(orderId); item.setSkuId(si.getSkuId()); item.setCount(si.getCount());
            item.setPrice(100L); item.setSkuName("SKU-" + si.getSkuId()); // 实际应调 product-service
            totalAmount += item.getPrice() * item.getCount();
            items.add(item);
        }

        OrderMain order = new OrderMain();
        order.setOrderId(orderId); order.setUserId(userId);
        order.setRequestId(req.getRequestId());
        order.setOrderStatus(OrderState.INIT.name());
        order.setTotalAmount(totalAmount); order.setPayAmount(totalAmount);
        order.setCouponId(req.getCouponId());
        orderMapper.insert(order);

        for (OrderItem item : items) itemMapper.insert(item);

        // 3. outbox
        Outbox outbox = new Outbox();
        outbox.setMessageId(IdUtil.fastSimpleUUID());
        outbox.setTopic("ORDER_CREATED"); outbox.setTag("CREATE");
        outbox.setPayload(JSONUtil.toJsonStr(new CreateOrderRequest()));
        outboxMapper.insert(outbox);

        log.info("订单创建: orderId={}, userId={}, amount={}", orderId, userId, totalAmount);
        return buildResponse(order);
    }

    @Override @Transactional
    public void cancel(Long userId, Long orderId) {
        OrderMain order = requireOrder(orderId);
        if (!order.getUserId().equals(userId)) throw new BizException(403, "无权操作");
        OrderState current = OrderState.from(order.getOrderStatus());
        OrderState.validateTransition(current, OrderState.CLOSED);
        order.setOrderStatus(OrderState.CLOSED.name());
        order.setCloseReason("用户取消");
        orderMapper.updateById(order);

        // 写 outbox 回滚消息
        Outbox outbox = new Outbox();
        outbox.setMessageId(IdUtil.fastSimpleUUID());
        outbox.setTopic("ORDER_CLOSED"); outbox.setTag("CLOSE");
        outbox.setPayload(JSONUtil.toJsonStr(order));
        outboxMapper.insert(outbox);

        log.info("订单取消: orderId={}", orderId);
    }

    @Override @Transactional
    public void payCallback(Long orderId) {
        OrderMain order = requireOrder(orderId);
        OrderState current = OrderState.from(order.getOrderStatus());
        OrderState.validateTransition(current, OrderState.PAID);
        order.setOrderStatus(OrderState.PAID.name());
        orderMapper.updateById(order);
        log.info("订单支付: orderId={}", orderId);
    }

    @Override
    public OrderResponse getByOrderId(Long orderId) {
        return buildResponse(requireOrder(orderId));
    }

    private OrderMain requireOrder(Long orderId) {
        OrderMain o = orderMapper.selectOne(new LambdaQueryWrapper<OrderMain>().eq(OrderMain::getOrderId, orderId));
        if (o == null) throw new BizException(404, "订单不存在");
        return o;
    }

    private OrderResponse buildResponse(OrderMain order) {
        List<OrderItem> items = itemMapper.selectList(
                new LambdaQueryWrapper<OrderItem>().eq(OrderItem::getOrderId, order.getOrderId()));
        return OrderResponse.builder()
                .orderId(order.getOrderId()).orderStatus(order.getOrderStatus())
                .totalAmount(order.getTotalAmount()).payAmount(order.getPayAmount())
                .items(items.stream().map(i -> OrderResponse.Item.builder()
                        .skuId(i.getSkuId()).skuName(i.getSkuName()).price(i.getPrice()).count(i.getCount()).build())
                        .collect(Collectors.toList()))
                .build();
    }
}