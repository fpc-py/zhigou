package com.zhigou.common.mask;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 日志脱敏标记：标注在 Controller/Service 方法（或类）上，
 * {@link SensitiveLogAspect} 会在方法入参/返回值的日志打印前自动掩码。
 *
 * <p>只影响日志输出，不改变业务数据与返回值本身。
 *
 * <pre>
 * &#64;SensitiveLog
 * &#64;PostMapping("/login")
 * public Result login(@RequestBody LoginRequest req) { ... }
 * </pre>
 */
@Documented
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface SensitiveLog {

    /** 是否打印方法耗时（默认 true） */
    boolean elapsed() default true;
}
