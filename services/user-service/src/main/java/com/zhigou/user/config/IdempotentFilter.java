package com.zhigou.user.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.util.ContentCachingResponseWrapper;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

/**
 * 写接口幂等过滤器：根据 X-Request-Id 缓存响应 5 分钟
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class IdempotentFilter implements Filter {

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper = new ObjectMapper();

    private static final String PREFIX = "idempotent:";
    private static final long TTL = 5; // 5 分钟

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        HttpServletRequest req = (HttpServletRequest) request;
        HttpServletResponse resp = (HttpServletResponse) response;

        String method = req.getMethod();
        String requestId = req.getHeader("X-Request-Id");

        // 只拦截写请求 + 带 requestId 的
        if (requestId == null || requestId.isBlank()
                || (!HttpMethod.POST.matches(method)
                && !HttpMethod.PUT.matches(method)
                && !HttpMethod.DELETE.matches(method))) {
            chain.doFilter(request, response);
            return;
        }

        String redisKey = PREFIX + requestId;

        // 命中缓存 → 直接返回
        String cached = redisTemplate.opsForValue().get(redisKey);
        if (cached != null) {
            log.info("幂等命中: requestId={}", requestId);
            resp.setContentType(MediaType.APPLICATION_JSON_VALUE);
            resp.setCharacterEncoding("UTF-8");
            resp.getWriter().write(cached);
            return;
        }

        // 未命中 → 包装响应以捕获 body
        ContentCachingResponseWrapper wrapper = new ContentCachingResponseWrapper(resp);
        chain.doFilter(request, wrapper);

        // 缓存响应
        byte[] body = wrapper.getContentAsByteArray();
        if (body.length > 0 && resp.getStatus() < 500) {
            String responseBody = new String(body, resp.getCharacterEncoding());
            redisTemplate.opsForValue().set(redisKey, responseBody, TTL, TimeUnit.MINUTES);
            log.info("幂等缓存: requestId={}", requestId);
        }
        wrapper.copyBodyToResponse();
    }
}