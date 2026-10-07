package com.zhigou.auth.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LoginResponse {

    /** Snowflake ID，序列化为字符串防 JS 精度丢失（CLAUDE.md 红线） */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long userId;
    private String accessToken;
    private String refreshToken;
    private long expiresIn;
}