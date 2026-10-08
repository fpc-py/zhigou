package com.zhigou.auth.dto;

import com.zhigou.common.mask.SensitiveField;
import com.zhigou.common.mask.SensitiveType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

@Data
public class LoginRequest {

    @NotBlank(message = "手机号不能为空")
    @Pattern(regexp = "^1[3-9]\\d{9}$", message = "手机号格式不正确")
    private String phone;

    /** 验证码：日志打印时按密码全掩 */
    @NotBlank(message = "验证码不能为空")
    @SensitiveField(SensitiveType.PASSWORD)
    private String code;
}