package com.zhigou.auth.controller;

import com.zhigou.auth.dto.LoginRequest;
import com.zhigou.auth.dto.LoginResponse;
import com.zhigou.auth.dto.SendSmsRequest;
import com.zhigou.auth.service.AuthService;
import com.zhigou.common.Result;
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

    @PostMapping("/send-sms-code")
    public Result<Void> sendSmsCode(@Valid @RequestBody SendSmsRequest request) {
        authService.sendSmsCode(request.getPhone());
        return Result.ok();
    }

    @PostMapping("/login")
    public Result<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        LoginResponse response = authService.login(request.getPhone(), request.getCode());
        return Result.ok(response);
    }
}