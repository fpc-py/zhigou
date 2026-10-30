package com.zhigou.order.controller;

import com.zhigou.common.Result;
import com.zhigou.order.dto.CreateOrderRequest;
import com.zhigou.order.dto.OrderResponse;
import com.zhigou.order.dto.OrderStatsOverview;
import com.zhigou.order.service.OrderService;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController @RequestMapping("/order") @RequiredArgsConstructor
public class OrderController {
    private final OrderService orderService;
    // UserContext.require() for JWT - simplified here

    @Operation(summary = "创建订单") @PostMapping("/create")
    public Result<OrderResponse> create(@Valid @RequestBody CreateOrderRequest req,
                                        @RequestHeader(value = "x-user-id", required = false) Long userId) {
        // 优先取网关/BFF 透传的 x-user-id；未透传时回退默认用户（兼容直连调试）
        return Result.ok(orderService.create(userId != null ? userId : 10001L, req));
    }

    @Operation(summary = "取消订单") @PostMapping("/{id}/cancel")
    public Result<Void> cancel(@PathVariable("id") Long id,
                               @RequestHeader(value = "x-user-id", required = false) Long userId) {
        orderService.cancel(userId != null ? userId : 10001L, id); return Result.ok();
    }

    @Operation(summary = "支付回调") @PostMapping("/payCallback/{orderId}")
    public Result<Void> payCallback(@PathVariable Long orderId) {
        orderService.payCallback(orderId); return Result.ok();
    }

    @Operation(summary = "商家订单列表（多商家隔离：按 x-merchant-id 过滤，分页；status 可空=全部）") @GetMapping("/merchant/list")
    public Result<Map<String, Object>> merchantList(@RequestHeader("x-merchant-id") Long merchantId,
                                                    @RequestParam(required = false) String status,
                                                    @RequestParam(defaultValue = "1") int pageNum,
                                                    @RequestParam(defaultValue = "20") int pageSize) {
        return Result.ok(orderService.merchantOrders(merchantId, status, pageNum, pageSize));
    }

    @Operation(summary = "商家订单详情（多商家隔离：归属校验，非本商家订单 403）") @GetMapping("/merchant/{orderId}")
    public Result<OrderResponse> merchantDetail(@RequestHeader("x-merchant-id") Long merchantId,
                                                @PathVariable("orderId") Long orderId) {
        return Result.ok(orderService.merchantDetail(merchantId, orderId));
    }

    @Operation(summary = "订单详情") @GetMapping("/{orderId}")
    public Result<OrderResponse> detail(@PathVariable Long orderId) {
        return Result.ok(orderService.getByOrderId(orderId));
    }

    @Operation(summary = "我的订单列表") @GetMapping("/mine")
    public Result<List<OrderResponse>> mine(@RequestParam Long userId) {
        return Result.ok(orderService.mine(userId));
    }

    @Operation(summary = "平台经营概览（商家视角：多商家隔离按归属过滤）") @GetMapping("/stats/overview")
    public Result<OrderStatsOverview> overview(@RequestHeader(value = "x-merchant-id", required = false) Long merchantId) {
        return Result.ok(orderService.overview(merchantId));
    }

    @Operation(summary = "履约异常：PAID 待发货订单 + SKU 明细（缺货/卡单预警；多商家隔离按归属过滤）") @GetMapping("/stats/pending-fulfillment")
    public Result<List<OrderResponse>> pendingFulfillment(@RequestHeader(value = "x-merchant-id", required = false) Long merchantId) {
        return Result.ok(orderService.pendingFulfillment(merchantId));
    }

    @Operation(summary = "异常订单自动处理：写处理动作（SPLIT/DELAY/OFF_SHELF/REPLENISH，演示口径）") @PostMapping("/fulfillment/action")
    public Result<Integer> fulfillmentAction(@RequestBody Map<String, Object> body) {
        String action = String.valueOf(body.get("action"));
        @SuppressWarnings("unchecked")
        List<Object> orderIdsRaw = (List<Object>) body.get("orderIds");
        // orderId 为 19 位 Snowflake：兼容字符串/数字两种入参，经 String 中转保证无损
        List<Long> orderIds = orderIdsRaw.stream()
                .map(id -> Long.valueOf(String.valueOf(id)))
                .collect(java.util.stream.Collectors.toList());
        String reason = body.get("reason") == null ? null : String.valueOf(body.get("reason"));
        return Result.ok(orderService.fulfillmentAction(action, orderIds, reason));
    }

    @Operation(summary = "最近异常订单处理记录") @GetMapping("/fulfillment/actions")
    public Result<List<com.zhigou.order.entity.FulfillmentAction>> fulfillmentActions(@RequestParam(defaultValue = "20") int limit) {
        return Result.ok(orderService.listFulfillmentActions(limit));
    }

    @Operation(summary = "部分发货：PAID→SHIPPING(部分)/SHIPPED(全部发完)，支持分批") @PostMapping("/{id}/ship-partial")
    public Result<Map<String, Object>> shipPartial(@PathVariable("id") Long id,
                                                   @RequestBody Map<String, Object> body) {
        Long skuId = Long.valueOf(String.valueOf(body.get("skuId")));
        Integer count = body.get("count") == null ? 1 : ((Number) body.get("count")).intValue();
        String trackingNo = body.get("trackingNo") == null ? null : String.valueOf(body.get("trackingNo"));
        return Result.ok(orderService.shipPartial(id, skuId, count, trackingNo));
    }

    @Operation(summary = "订单发货明细") @GetMapping("/{id}/shipments")
    public Result<List<com.zhigou.order.entity.OrderShipment>> shipments(@PathVariable("id") Long id) {
        return Result.ok(orderService.shipments(id));
    }
}