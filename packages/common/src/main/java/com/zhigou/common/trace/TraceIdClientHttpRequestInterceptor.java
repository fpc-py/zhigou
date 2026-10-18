package com.zhigou.common.trace;

import org.slf4j.MDC;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;

import java.io.IOException;

/**
 * RestTemplate 出站 traceId 透传拦截器（工作流3 #10）。
 *
 * 从本线程 MDC 取 traceId 写入出站请求 x-trace-id 头，
 * 下游服务 TraceIdFilter 读到后复用同一 traceId → 跨服务日志串成一条链。
 *
 * 关键坑：RestTemplate 默认同步执行，同一线程 MDC 可见；若未来换 WebClient/异步线程池，
 * 需显式传递 MDC（本拦截器不感知线程切换，这是同步模型的隐含假设）。
 */
public class TraceIdClientHttpRequestInterceptor implements ClientHttpRequestInterceptor {

    @Override
    public ClientHttpResponse intercept(HttpRequest request, byte[] body,
                                        ClientHttpRequestExecution execution) throws IOException {
        String traceId = MDC.get(TraceIdFilter.MDC_KEY);
        if (traceId != null && !traceId.isBlank()) {
            HttpHeaders headers = request.getHeaders();
            if (!headers.containsKey(TraceIdFilter.HEADER)) {
                headers.set(TraceIdFilter.HEADER, traceId);
            }
        }
        return execution.execute(request, body);
    }
}
