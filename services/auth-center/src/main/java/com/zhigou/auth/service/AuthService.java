package com.zhigou.auth.service;

import com.zhigou.auth.dto.LoginResponse;

public interface AuthService {

    /**
     * 发送短信验证码
     */
    void sendSmsCode(String phone);

    /**
     * 验证码登录
     */
    LoginResponse login(String phone, String code);
}