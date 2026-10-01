package com.zhigou.cart.interceptor;

public class UserContext {
    private static final ThreadLocal<Long> H = new ThreadLocal<>();
    public static void set(Long v) { H.set(v); }
    public static Long get() { return H.get(); }
    public static Long require() { Long v = H.get(); if (v == null) throw new com.zhigou.common.BizException(401, "未登录"); return v; }
    public static void clear() { H.remove(); }
}