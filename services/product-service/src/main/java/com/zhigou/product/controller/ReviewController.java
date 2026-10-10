package com.zhigou.product.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zhigou.common.Result;
import com.zhigou.common.auth.UserContext;
import com.zhigou.product.dto.ReviewStatsVO;
import com.zhigou.product.dto.ReviewSubmitRequest;
import com.zhigou.product.dto.ReviewVO;
import com.zhigou.product.service.ProductReviewService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 商品评价 Controller（P1 四批：评价数据底座）。
 * 供 H5 商品详情/AI review_analysis 调用；统计含差评/水军特征指标。
 */
@Slf4j
@Tag(name = "商品评价")
@RestController
@RequestMapping("/product/review")
@RequiredArgsConstructor
public class ReviewController {

    private final ProductReviewService reviewService;

    @Operation(summary = "发表评价")
    @PostMapping
    public Result<Map<String, String>> submit(@Valid @RequestBody ReviewSubmitRequest request) {
        Long userId = currentUserId();
        // reviewId 为 Snowflake ID，转字符串返回避免前端精度丢失
        return Result.ok(Map.of("reviewId", String.valueOf(reviewService.submit(userId, request))));
    }

    @Operation(summary = "评价列表（可按星级区间过滤）")
    @GetMapping
    public Result<Page<ReviewVO>> page(@RequestParam Long spuId,
                                       @RequestParam(required = false) Integer minRating,
                                       @RequestParam(required = false) Integer maxRating,
                                       @RequestParam(defaultValue = "1") int pageNum,
                                       @RequestParam(defaultValue = "20") int pageSize) {
        return Result.ok(reviewService.page(spuId, minRating, maxRating, pageNum, pageSize));
    }

    @Operation(summary = "评价统计（总数/均分/星级分布/晒图/差评/重复内容）")
    @GetMapping("/stats")
    public Result<ReviewStatsVO> stats(@RequestParam Long spuId) {
        return Result.ok(reviewService.stats(spuId));
    }

    @Operation(summary = "全平台低分评价列表（商家负面预警，演示口径）")
    @GetMapping("/negative")
    public Result<java.util.List<ReviewVO>> negative(@RequestParam(defaultValue = "3") int minRating,
                                                     @RequestParam(defaultValue = "5") int limit) {
        return Result.ok(reviewService.negative(minRating, limit));
    }

    /** 从请求上下文取当前用户（JwtAuthFilter 已解析 x-user-id / JWT） */
    private Long currentUserId() {
        return UserContext.getUserId();
    }
}
