package com.zhigou.order.service;

import com.zhigou.order.dto.CreateOrderRequest;
import com.zhigou.order.dto.OrderResponse;
import com.zhigou.order.dto.OrderStatsOverview;

import java.util.List;

public interface OrderService {
    OrderResponse create(Long userId, CreateOrderRequest req);
    void cancel(Long userId, Long orderId);
    void payCallback(Long orderId);   // INIT → PAID
    OrderResponse getByOrderId(Long orderId);
    List<OrderResponse> mine(Long userId);   // 按 userId 查订单列表（创建时间倒序）
    /** 超时关单：关闭超过 minutes 分钟仍未支付的 INIT 订单（INIT→CLOSED + outbox + 释放库存），返回关单数 */
    int closeExpired(int minutes);
    /** 平台经营概览（全平台聚合，商家视角演示口径） */
    OrderStatsOverview overview();
    /** 履约异常：PAID 待发货订单 + SKU 明细（供缺货/卡单预警） */
    List<OrderResponse> pendingFulfillment();
    /** 异常订单自动处理：对指定 PAID 订单写处理动作记录（SPLIT/DELAY/OFF_SHELF/REPLENISH，演示口径直接标记 DONE），返回处理单数 */
    int fulfillmentAction(String action, List<Long> orderIds, String reason);
    /** 最近异常订单处理记录（按时间倒序） */
    List<com.zhigou.order.entity.FulfillmentAction> listFulfillmentActions(int limit);
}