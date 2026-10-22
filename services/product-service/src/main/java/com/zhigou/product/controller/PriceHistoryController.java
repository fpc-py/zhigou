package com.zhigou.product.controller;

import com.zhigou.common.Result;
import com.zhigou.product.dto.PriceHistoryVO;
import com.zhigou.product.service.PriceHistoryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * 历史价格接口（AI 省钱助手 · A 块）。
 * 趋势 + 最低价 + 现价对比，支撑降价提醒与省钱汇总。
 */
@Tag(name = "历史价格")
@RestController
@RequestMapping("/price")
@RequiredArgsConstructor
public class PriceHistoryController {

    private final PriceHistoryService priceHistoryService;

    @Operation(summary = "历史价格趋势（近 N 天每日价格 + 最低价 + 现价对比）")
    @GetMapping("/history")
    public Result<PriceHistoryVO> history(
            @RequestParam Long skuId,
            @RequestParam(defaultValue = "30") int days) {
        return Result.ok(priceHistoryService.history(skuId, days));
    }

    @Operation(summary = "触发每日全量快照（运维/调试用）")
    @PostMapping("/history/snapshot")
    public Result<Integer> snapshot() {
        return Result.ok(priceHistoryService.snapshotAll());
    }
}
