package com.zhigou.order.service;

import com.zhigou.order.dto.CreateOrderRequest;
import com.zhigou.order.dto.OrderResponse;

import java.util.List;

public interface OrderService {
    OrderResponse create(Long userId, CreateOrderRequest req);
    void cancel(Long userId, Long orderId);
    void payCallback(Long orderId);   // INIT → PAID
    OrderResponse getByOrderId(Long orderId);
    List<OrderResponse> mine(Long userId);   // 按 userId 查订单列表（创建时间倒序）
    /** 超时关单：关闭超过 minutes 分钟仍未支付的 INIT 订单（INIT→CLOSED + outbox + 释放库存），返回关单数 */
    int closeExpired(int minutes);
}