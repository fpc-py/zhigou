package com.zhigou.common.security;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;

/**
 * B3 安全头 + XSS 过滤自动配置：所有引入 common 的服务全局生效。
 */
@AutoConfiguration
public class SecurityHeadersAutoConfiguration {

    @Bean
    public FilterRegistrationBean<XssFilter> xssFilterRegistration() {
        FilterRegistrationBean<XssFilter> reg = new FilterRegistrationBean<>(new XssFilter());
        reg.addUrlPatterns("/*");
        reg.setOrder(-200); // 在鉴权过滤器之前，最先拦截恶意参数
        return reg;
    }
}
