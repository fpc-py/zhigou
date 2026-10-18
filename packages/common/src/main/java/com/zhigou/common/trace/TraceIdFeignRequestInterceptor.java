package com.zhigou.common.trace;

import feign.RequestInterceptor;
import feign.RequestTemplate;
import org.slf4j.MDC;

/**
 * OpenFeign 出站 traceId 透传拦截器（工作流3 #10）。
 *
 * aftersale-service 使用 OpenFeign 调下游，Feign 自动收集所有 RequestInterceptor bean，
 * 这里从 MDC 取 traceId 写入请求头，与 RestTemplate 拦截器行为一致。
 */
public class TraceIdFeignRequestInterceptor implements RequestInterceptor {

    @Override
    public void apply(RequestTemplate template) {
        String traceId = MDC.get(TraceIdFilter.MDC_KEY);
        if (traceId != null && !traceId.isBlank()) {
            template.header(TraceIdFilter.HEADER, traceId);
        }
    }
}
