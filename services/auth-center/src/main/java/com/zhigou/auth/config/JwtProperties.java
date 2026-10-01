package com.zhigou.auth.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "jwt")
public class JwtProperties {

    /** JWT 签名密钥（HMAC-SHA256） */
    private String secret;

    /** accessToken 过期秒数，默认 7200（2 小时） */
    private long accessExpireSeconds = 7200;

    /** refreshToken 过期秒数，默认 1209600（14 天） */
    private long refreshExpireSeconds = 1209600;
}