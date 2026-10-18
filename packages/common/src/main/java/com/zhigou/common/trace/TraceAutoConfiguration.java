package com.zhigou.common.trace;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.context.annotation.Bean;

/**
 * traceId 全链路自动配置（工作流3 #10）。
 *
 * 注册 TraceIdFilter —— OncePerRequestFilter 作为 @Bean 会被 Servlet 容器自动加入过滤器链
 * （无需手动 filterRegistrationBean），优先级最高，先于 JwtAuthFilter 生效。
 *
 * Feign 拦截器拆分到 TraceFeignAutoConfiguration（@ConditionalOnClass 按需装配）：
 * 公共库 openfeign 为 optional 依赖不传递，若本类方法签名引用 feign 类型，
 * 无 Feign 的服务加载本类会 NoClassDefFoundError → 拆类是必要条件（真实踩坑）。
 *
 * RestTemplate 拦截器不进公共库自动配置：Spring Boot 的 RestTemplateCustomizer
 * 只作用于自动装配的 RestTemplate，各服务手动 new 的 bean 不受影响，
 * 因此由 cart/order 各自的 RestTemplateConfig 显式注入（保留各自超时配置）。
 */
@AutoConfiguration
public class TraceAutoConfiguration {

    @Bean
    public TraceIdFilter traceIdFilter() {
        return new TraceIdFilter();
    }
}
