package com.zhigou.auth.dto;

import jakarta.validation.constraints.Pattern;
import lombok.Data;

@Data
public class SendSmsRequest {

    @Pattern(regexp = "^1[3-9]\\d{9}$", message = "手机号格式不正确")
    private String phone;
}