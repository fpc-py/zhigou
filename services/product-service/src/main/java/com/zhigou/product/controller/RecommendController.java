package com.zhigou.product.controller;

import com.zhigou.common.Result;
import com.zhigou.common.auth.UserContext;
import com.zhigou.product.dto.RecommendResponse;
import com.zhigou.product.service.RecommendService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * 个性化推荐接口（P1 五批）。
 * 用户身份由服务端 UserContext 注入（内网 x-user-id 透传），不信任外部入参。
 */
@Tag(name = "推荐")
@RestController
@RequestMapping("/recommend")
@RequiredArgsConstructor
public class RecommendController {

    private final RecommendService recommendService;

    @Operation(summary = "个性化推荐（画像 = 历史订单 + 购物车 + 评价口碑）")
    @GetMapping
    public Result<RecommendResponse> recommend(
            @RequestParam(defaultValue = "home") String scene,
            @RequestParam(defaultValue = "6") int limit) {
        Long userId = UserContext.getUserId();
        return Result.ok(recommendService.recommend(userId, scene, limit));
    }
}
