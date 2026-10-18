package com.zhigou.common.trace;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.context.annotation.Bean;

/**
 * Feign traceId 透传自动配置（工作流3 #10）。
 *
 * @ConditionalOnClass(EnableFeignClients.class)：仅当服务启用 OpenFeign 时才装配，
 * 无 Feign 依赖的服务（product/order/cart 等）不加载本类，避免 NoClassDefFoundError。
 * Feign 自动收集所有 RequestInterceptor bean → 所有 OpenFeign 出站调用自动透传 x-trace-id。
 */
@AutoConfiguration
@ConditionalOnClass(EnableFeignClients.class)
public class TraceFeignAutoConfiguration {

    @Bean
    public TraceIdFeignRequestInterceptor traceIdFeignRequestInterceptor() {
        return new TraceIdFeignRequestInterceptor();
    }
}
