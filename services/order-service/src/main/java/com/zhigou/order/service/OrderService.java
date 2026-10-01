package com.zhigou.order.service;

import com.zhigou.order.dto.CreateOrderRequest;
import com.zhigou.order.dto.OrderResponse;

public interface OrderService {
    OrderResponse create(Long userId, CreateOrderRequest req);
    void cancel(Long userId, Long orderId);
    void payCallback(Long orderId);   // INIT → PAID
    OrderResponse getByOrderId(Long orderId);
}