package com.zhigou.product.controller;

import com.zhigou.common.Result;
import com.zhigou.product.dto.PriceAlertVO;
import com.zhigou.product.dto.PriceNoticeVO;
import com.zhigou.product.service.PriceAlertService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 降价关注与提醒接口（AI 省钱助手 · B 块）。
 * 关注制：用户主动关注才产生提醒，默认零推送。
 */
@Tag(name = "降价提醒")
@RestController
@RequestMapping("/price")
@RequiredArgsConstructor
public class PriceAlertController {

    private final PriceAlertService priceAlertService;

    @Operation(summary = "关注降价（幂等 upsert；targetPrice 可空=任意降价提醒）")
    @PostMapping("/alert")
    public Result<PriceAlertVO> watch(@RequestHeader(value = "X-User-Id", required = false) Long userId,
                                      @RequestBody Map<String, Object> body) {
        Long skuId = Long.valueOf(String.valueOf(body.get("skuId")));
        Object tp = body.get("targetPrice");
        Long targetPrice = tp == null ? null : Long.valueOf(String.valueOf(tp));
        return Result.ok(priceAlertService.watch(userId, skuId, targetPrice));
    }

    @Operation(summary = "取消关注")
    @DeleteMapping("/alert/{skuId}")
    public Result<Void> unwatch(@RequestHeader(value = "X-User-Id", required = false) Long userId,
                                @PathVariable Long skuId) {
        priceAlertService.unwatch(userId, skuId);
        return Result.ok(null);
    }

    @Operation(summary = "我的关注列表（含当前价/是否已降）")
    @GetMapping("/alert/mine")
    public Result<List<PriceAlertVO>> mine(@RequestHeader(value = "X-User-Id", required = false) Long userId) {
        return Result.ok(priceAlertService.mine(userId));
    }

    @Operation(summary = "我的降价提醒（unreadOnly=true 仅未读）")
    @GetMapping("/notice/mine")
    public Result<List<PriceNoticeVO>> notices(@RequestHeader(value = "X-User-Id", required = false) Long userId,
                                               @RequestParam(defaultValue = "false") boolean unreadOnly) {
        return Result.ok(priceAlertService.notices(userId, unreadOnly));
    }

    @Operation(summary = "标记提醒已读")
    @PostMapping("/notice/read/{noticeId}")
    public Result<Integer> markRead(@RequestHeader(value = "X-User-Id", required = false) Long userId,
                                    @PathVariable Long noticeId) {
        return Result.ok(priceAlertService.markRead(userId, noticeId));
    }

    @Operation(summary = "触发每日降价检测（运维/调试用）")
    @PostMapping("/alert/check")
    public Result<Integer> check(@RequestHeader(value = "X-User-Id", required = false) Long userId) {
        return Result.ok(priceAlertService.checkDrops());
    }
}
