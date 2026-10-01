package com.zhigou.order.controller;

import com.zhigou.common.Result;
import com.zhigou.order.dto.CreateOrderRequest;
import com.zhigou.order.dto.OrderResponse;
import com.zhigou.order.service.OrderService;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController @RequestMapping("/order") @RequiredArgsConstructor
public class OrderController {
    private final OrderService orderService;
    // UserContext.require() for JWT - simplified here

    @Operation(summary = "创建订单") @PostMapping("/create")
    public Result<OrderResponse> create(@Valid @RequestBody CreateOrderRequest req) {
        return Result.ok(orderService.create(10001L, req)); // JWT userId in real impl
    }

    @Operation(summary = "取消订单") @PostMapping("/{id}/cancel")
    public Result<Void> cancel(@PathVariable("id") Long id) {
        orderService.cancel(10001L, id); return Result.ok();
    }

    @Operation(summary = "支付回调") @PostMapping("/payCallback/{orderId}")
    public Result<Void> payCallback(@PathVariable Long orderId) {
        orderService.payCallback(orderId); return Result.ok();
    }

    @Operation(summary = "订单详情") @GetMapping("/{orderId}")
    public Result<OrderResponse> detail(@PathVariable Long orderId) {
        return Result.ok(orderService.getByOrderId(orderId));
    }
}