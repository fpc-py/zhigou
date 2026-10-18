package com.zhigou.common.trace;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

/**
 * traceId 全链路注入过滤器（工作流3 #10）。
 *
 * 职责：
 * 1. 入站请求读取 x-trace-id 头（上游透传场景复用同一 traceId），无则生成；
 * 2. 写入 MDC（key=traceId），使本服务所有日志行携带 traceId；
 * 3. 响应头回写 x-trace-id，调用方能关联整条调用链；
 * 4. 请求结束清理 MDC，防止线程池复用导致 traceId 串线（关键坑）。
 *
 * 注册为最高优先级（比 JwtAuthFilter 早执行），保证鉴权日志也带 traceId。
 * 生成规则：UUID 前 12 位（去掉横线），兼顾可读性与唯一性。
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class TraceIdFilter extends OncePerRequestFilter {

    public static final String HEADER = "x-trace-id";
    public static final String MDC_KEY = "traceId";

    @Override
    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res,
                                    FilterChain chain) throws ServletException, IOException {
        String traceId = req.getHeader(HEADER);
        if (traceId == null || traceId.isBlank() || traceId.length() > 64) {
            traceId = generateTraceId();
        }
        // 防 MDC 串线：先清再放（同一线程被池化复用时，前一次请求可能残留）
        MDC.remove(MDC_KEY);
        MDC.put(MDC_KEY, traceId);
        res.setHeader(HEADER, traceId);
        try {
            chain.doFilter(req, res);
        } finally {
            MDC.remove(MDC_KEY);
        }
    }

    public static String generateTraceId() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 12);
    }
}
