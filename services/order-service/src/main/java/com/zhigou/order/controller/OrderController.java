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

    @Operation(summary = "订单详情") @GetMapping("/{orderId}")
    public Result<OrderResponse> detail(@PathVariable Long orderId) {
        return Result.ok(orderService.getByOrderId(orderId));
    }

    @Operation(summary = "我的订单列表") @GetMapping("/mine")
    public Result<List<OrderResponse>> mine(@RequestParam Long userId) {
        return Result.ok(orderService.mine(userId));
    }

    @Operation(summary = "平台经营概览（商家视角，演示口径）") @GetMapping("/stats/overview")
    public Result<OrderStatsOverview> overview() {
        return Result.ok(orderService.overview());
    }

    @Operation(summary = "履约异常：PAID 待发货订单 + SKU 明细（缺货/卡单预警）") @GetMapping("/stats/pending-fulfillment")
    public Result<List<OrderResponse>> pendingFulfillment() {
        return Result.ok(orderService.pendingFulfillment());
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
}