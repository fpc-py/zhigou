package com.zhigou.product.controller;

import com.zhigou.common.Result;
import com.zhigou.product.dto.CostSaverOverviewVO;
import com.zhigou.product.service.CostSaverService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/** 省钱助手总览接口（AI 省钱助手 · C 块）。第一屏可感知：本月帮你省了 X 元。 */
@Tag(name = "省钱助手")
@RestController
@RequestMapping("/cost-saver")
@RequiredArgsConstructor
public class CostSaverController {

    private final CostSaverService costSaverService;

    @Operation(summary = "省钱总览（满减实省 + 最优券预估 + 比价最优差 + 降价已省）")
    @GetMapping("/overview")
    public Result<CostSaverOverviewVO> overview(
            @RequestHeader(value = "X-User-Id", required = false) Long userId) {
        return Result.ok(costSaverService.overview(userId));
    }
}
