package com.zhigou.marketing.controller;

import com.zhigou.common.Result;
import com.zhigou.marketing.dto.CalculateRequest;
import com.zhigou.marketing.dto.CalculateResponse;
import com.zhigou.marketing.entity.UserCoupon;
import com.zhigou.marketing.service.CouponService;
import com.zhigou.marketing.service.impl.MarketingServiceImpl;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.Map;

@RestController @RequestMapping(produces = "application/json") @RequiredArgsConstructor
public class MarketingController {
    private final CouponService couponService;
    private final MarketingServiceImpl marketingService;

    @GetMapping("/marketing/plan")
    public Result<List<Map<String, Object>>> plan() {
        return Result.ok(marketingService.marketingPlan());
    }

    @PostMapping("/coupon/issue")
    public Result<Long> issue(@RequestBody Map<String, Long> body) {
        return Result.ok(couponService.issue(body.get("userId"), body.get("templateId")));
    }

    @GetMapping("/coupon/mine")
    public Result<List<UserCoupon>> mine(@RequestParam Long userId, @RequestParam(required = false) String status) {
        return Result.ok(couponService.mine(userId, status));
    }

    @PostMapping("/discount/calculate")
    public Result<CalculateResponse> calculate(@RequestBody CalculateRequest req) {
        return Result.ok(marketingService.calculate(req));
    }

    @PostMapping("/discount/pre-freeze")
    public Result<Void> preFreeze(@RequestBody Map<String, Object> body) {
        Long userId = ((Number) body.get("userId")).longValue();
        Long couponId = ((Number) body.get("couponId")).longValue();
        Long orderId = ((Number) body.get("orderId")).longValue();
        couponService.preFreeze(userId, couponId, orderId);
        return Result.ok();
    }

    @PostMapping("/discount/confirm")
    public Result<Void> confirm(@RequestBody Map<String, Object> body) {
        couponService.confirm(((Number) body.get("orderId")).longValue());
        return Result.ok();
    }

    @PostMapping("/discount/release")
    public Result<Void> release(@RequestBody Map<String, Object> body) {
        couponService.release(((Number) body.get("orderId")).longValue());
        return Result.ok();
    }
}