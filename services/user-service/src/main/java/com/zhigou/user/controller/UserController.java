package com.zhigou.user.controller;

import com.zhigou.common.Result;
import com.zhigou.user.dto.RegisterRequest;
import com.zhigou.user.dto.UserProfileRequest;
import com.zhigou.user.dto.UserProfileResponse;
import com.zhigou.user.interceptor.UserContext;
import com.zhigou.user.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@Tag(name = "用户")
@RestController
@RequestMapping("/user")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @Operation(summary = "自动建档")
    @PostMapping("/register-if-absent")
    public Result<Map<String, Long>> registerIfAbsent(@Valid @RequestBody RegisterRequest request) {
        Long userId = UserContext.requireUserId();
        Long resultUserId = userService.registerIfAbsent(userId, request.getPhone());
        return Result.ok(Map.of("user_id", resultUserId));
    }

    @Operation(summary = "查询当前用户资料")
    @GetMapping("/profile")
    public Result<UserProfileResponse> getProfile() {
        Long userId = UserContext.requireUserId();
        return Result.ok(userService.getProfile(userId));
    }

    @Operation(summary = "修改用户资料")
    @PutMapping("/profile")
    public Result<UserProfileResponse> updateProfile(@RequestBody UserProfileRequest request) {
        Long userId = UserContext.requireUserId();
        return Result.ok(userService.updateProfile(userId, request));
    }
}