package com.zhigou.auth.controller;

import com.zhigou.auth.dto.LoginRequest;
import com.zhigou.auth.dto.LoginResponse;
import com.zhigou.auth.dto.SendSmsRequest;
import com.zhigou.auth.service.AuthService;
import com.zhigou.common.Result;
import com.zhigou.common.mask.SensitiveLog;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    /** 发短信：日志打印时手机号自动掩码 */
    @SensitiveLog
    @PostMapping("/send-sms-code")
    public Result<Void> sendSmsCode(@Valid @RequestBody SendSmsRequest request) {
        authService.sendSmsCode(request.getPhone());
        return Result.ok();
    }

    /** 登录：入参手机号/验证码自动掩码，返回 accessToken 全掩 */
    @SensitiveLog
    @PostMapping("/login")
    public Result<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        LoginResponse response = authService.login(request.getPhone(), request.getCode());
        return Result.ok(response);
    }
}