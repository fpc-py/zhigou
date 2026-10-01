package com.zhigou.cart.config;

import com.zhigou.cart.interceptor.UserIdInterceptor;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration @RequiredArgsConstructor
public class WebMvcConfig implements WebMvcConfigurer {
    private final UserIdInterceptor interceptor;
    @Override public void addInterceptors(InterceptorRegistry r) { r.addInterceptor(interceptor).addPathPatterns("/**"); }
}