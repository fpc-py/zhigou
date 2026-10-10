package com.zhigou.user.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zhigou.common.Result;
import com.zhigou.user.dto.ProductTrackItem;
import com.zhigou.user.dto.ProductTrackRequest;
import com.zhigou.user.dto.UserInsightResponse;
import com.zhigou.user.interceptor.UserContext;
import com.zhigou.user.service.UserProfileService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 用户画像底座：收藏 / 浏览历史 / 画像洞察
 */
@Tag(name = "用户画像底座")
@RestController
@RequestMapping("/user")
@RequiredArgsConstructor
public class UserProfileController {

    private final UserProfileService userProfileService;

    @Operation(summary = "收藏（幂等）")
    @PostMapping("/favorite")
    public Result<ProductTrackItem> addFavorite(@Valid @RequestBody ProductTrackRequest request) {
        Long userId = UserContext.requireUserId();
        return Result.ok(userProfileService.addFavorite(userId, request));
    }

    @Operation(summary = "取消收藏")
    @DeleteMapping("/favorite/{spuId}")
    public Result<Void> removeFavorite(@PathVariable Long spuId) {
        Long userId = UserContext.requireUserId();
        userProfileService.removeFavorite(userId, spuId);
        return Result.ok(null);
    }

    @Operation(summary = "我的收藏（分页）")
    @GetMapping("/favorite")
    public Result<Page<ProductTrackItem>> listFavorites(@RequestParam(defaultValue = "1") int page,
                                                        @RequestParam(defaultValue = "10") int size) {
        Long userId = UserContext.requireUserId();
        return Result.ok(userProfileService.listFavorites(userId, Math.max(page, 1), Math.min(Math.max(size, 1), 50)));
    }

    @Operation(summary = "已收藏 SPU ID 集合（详情页判断）")
    @GetMapping("/favorite/ids")
    public Result<List<String>> favoriteIds() {
        Long userId = UserContext.requireUserId();
        return Result.ok(userProfileService.favoriteIds(userId));
    }

    @Operation(summary = "记录浏览（同 SPU 聚合）")
    @PostMapping("/browse")
    public Result<ProductTrackItem> recordBrowse(@Valid @RequestBody ProductTrackRequest request) {
        Long userId = UserContext.requireUserId();
        return Result.ok(userProfileService.recordBrowse(userId, request));
    }

    @Operation(summary = "最近浏览")
    @GetMapping("/browse/recent")
    public Result<List<ProductTrackItem>> recentBrowse(@RequestParam(defaultValue = "20") int limit) {
        Long userId = UserContext.requireUserId();
        return Result.ok(userProfileService.recentBrowse(userId, limit));
    }

    @Operation(summary = "用户画像洞察（供 AI Agent 内网直连）")
    @GetMapping("/insight")
    public Result<UserInsightResponse> insight() {
        Long userId = UserContext.requireUserId();
        return Result.ok(userProfileService.insight(userId));
    }

    @Operation(summary = "收藏状态（演示：合并 favorite/ids 快捷判断）")
    @GetMapping("/favorite/check")
    public Result<Map<String, Boolean>> checkFavorite(@RequestParam Long spuId) {
        Long userId = UserContext.requireUserId();
        boolean fav = userProfileService.favoriteIds(userId).contains(String.valueOf(spuId));
        return Result.ok(Map.of("favorited", fav));
    }
}
