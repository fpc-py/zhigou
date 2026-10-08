package com.zhigou.common.mask;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 敏感字段注解：标注在 DTO/实体字段上，日志脱敏时按指定类型掩码。
 * 适用于字段名不在敏感词表（如 code、verifyCode 等通用名）。
 *
 * <pre>
 * public class LoginRequest {
 *     private String phone;
 *
 *     {@code @SensitiveField(SensitiveType.PASSWORD)}
 *     private String code;
 * }
 * </pre>
 */
@Documented
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
public @interface SensitiveField {

    SensitiveType value() default SensitiveType.DEFAULT;
}
