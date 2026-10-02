package com.zhigou.common.auth;

/**
 * 当前请求用户上下文（ThreadLocal）。
 * 由 JwtAuthFilter 在请求入口设置，业务层通过 getUserId() 获取。
 * 请求结束后必须 clear()，防止内存泄漏。
 */
public class UserContext {

    private static final ThreadLocal<Long> userIdHolder = new ThreadLocal<>();

    public static void setUserId(Long userId) {
        userIdHolder.set(userId);
    }

    public static Long getUserId() {
        return userIdHolder.get();
    }

    public static void clear() {
        userIdHolder.remove();
    }
}