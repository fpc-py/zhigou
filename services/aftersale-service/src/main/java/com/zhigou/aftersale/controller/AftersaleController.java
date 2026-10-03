package com.zhigou.aftersale.controller;

import com.zhigou.aftersale.dto.ApplyRequest;
import com.zhigou.aftersale.entity.AftersaleOrder;
import com.zhigou.aftersale.service.impl.AftersaleServiceImpl;
import com.zhigou.common.Result;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.Map;

@RestController @RequestMapping("/aftersale") @RequiredArgsConstructor
public class AftersaleController {
    private final AftersaleServiceImpl service;

    @PostMapping("/apply")
    public Result<AftersaleOrder> apply(@Valid @RequestBody ApplyRequest req, @RequestHeader(required = false) Long userId) {
        return Result.ok(service.apply(userId != null ? userId : 10001L, req));
    }

    @GetMapping("/mine")
    public Result<List<AftersaleOrder>> mine(@RequestParam Long userId) {
        return Result.ok(service.mine(userId));
    }

    @GetMapping("/{no}")
    public Result<AftersaleOrder> detail(@PathVariable String no) { return Result.ok(service.detail(no)); }

    @PostMapping("/{no}/cancel")
    public Result<Void> cancel(@PathVariable String no, @RequestParam Long userId) {
        service.cancel(userId, no); return Result.ok();
    }

    @PostMapping("/{no}/approve")
    public Result<Void> approve(@PathVariable String no) { service.approve(no); return Result.ok(); }

    @PostMapping("/{no}/refund")
    public Result<Void> refund(@PathVariable String no) {
        service.startRefunding(no);
        service.onRefundSuccess(no);
        return Result.ok();
    }

    @PostMapping("/{no}/reject")
    public Result<Void> reject(@PathVariable String no, @RequestBody Map<String, String> body) {
        service.reject(no, body.get("reason")); return Result.ok();
    }
}