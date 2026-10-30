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
    /** 平台经营概览（商家视角：merchantId 非空按商家过滤，多商家隔离；null=平台聚合） */
    OrderStatsOverview overview(Long merchantId);
    /** 履约异常：PAID 待发货订单 + SKU 明细（供缺货/卡单预警；merchantId 非空按商家过滤） */
    List<OrderResponse> pendingFulfillment(Long merchantId);
    /** 异常订单自动处理：对指定 PAID 订单写处理动作记录（SPLIT/DELAY/OFF_SHELF/REPLENISH，演示口径直接标记 DONE），返回处理单数 */
    int fulfillmentAction(String action, List<Long> orderIds, String reason);
    /** 最近异常订单处理记录（按时间倒序） */
    List<com.zhigou.order.entity.FulfillmentAction> listFulfillmentActions(int limit);

    /**
     * 部分发货：PAID → SHIPPING(部分) / 全部发完 → SHIPPED
     * @return { orderStatus, shippedSkuCount, remainingCount, shipments }
     */
    java.util.Map<String, Object> shipPartial(Long orderId, Long skuId, Integer count, String trackingNo);

    /** 订单发货明细 */
    List<com.zhigou.order.entity.OrderShipment> shipments(Long orderId);
}