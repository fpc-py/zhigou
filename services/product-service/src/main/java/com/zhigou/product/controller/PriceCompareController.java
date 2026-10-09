package com.zhigou.product.controller;

import com.zhigou.common.Result;
import com.zhigou.product.dto.PriceCompareResponse;
import com.zhigou.product.service.PriceCompareService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 跨平台比价接口（P1 六批）。
 * 渠道为本地模拟数据源（可替换为真实第三方比价 API，见 PriceSourceAdapter 实现说明）。
 */
@Tag(name = "比价")
@RestController
@RequestMapping("/price")
@RequiredArgsConstructor
public class PriceCompareController {

    private final PriceCompareService priceCompareService;

    @Operation(summary = "跨平台比价（多 SKU 聚合各渠道报价 + 最优购买方案）")
    @PostMapping("/compare")
    public Result<List<PriceCompareResponse>> compare(@RequestBody List<Long> skuIds) {
        return Result.ok(priceCompareService.compare(skuIds));
    }
}
