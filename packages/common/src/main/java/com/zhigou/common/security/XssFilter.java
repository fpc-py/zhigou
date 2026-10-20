package com.zhigou.common.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.regex.Pattern;

/**
 * B3 反射型 XSS 过滤：URL query 命中攻击特征（&lt;script / onerror= / javascript: 等）→ 400 拒绝；
 * 同时为所有响应附加安全头（CSP / X-Content-Type-Options / X-Frame-Options / Referrer-Policy）。
 * 说明：本项目 JWT 无状态（Authorization 头），无 Cookie 会话，天然免疫传统 CSRF；
 * 安全头中 X-Frame-Options 亦防止点击劫持。
 */
@Slf4j
public class XssFilter extends OncePerRequestFilter {

    private static final Pattern[] XSS_PATTERNS = {
            Pattern.compile("(?i)<script[\\s>]"),
            Pattern.compile("(?i)<iframe[\\s>]"),
            Pattern.compile("(?i)<svg[\\s>]"),
            Pattern.compile("(?i)onerror\\s*="),
            Pattern.compile("(?i)onload\\s*="),
            Pattern.compile("(?i)onclick\\s*="),
            Pattern.compile("(?i)javascript\\s*:"),
            Pattern.compile("(?i)<img[\\s>][^>]*on"),
            Pattern.compile("(?i)expression\\s*\\(")
    };

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        // 1) 反射型 XSS：URL query 检查
        String qs = request.getQueryString();
        if (qs != null && !qs.isBlank() && matches(qs)) {
            log.warn("XSS 请求被拦截: {} {}?{}", request.getMethod(), request.getRequestURI(),
                    qs.length() > 200 ? qs.substring(0, 200) : qs);
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            response.setCharacterEncoding(StandardCharsets.UTF_8.name());
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write("{\"code\":40010,\"message\":\"非法请求参数\"}");
            return;
        }

        // 2) 安全响应头
        response.setHeader("Content-Security-Policy",
                "default-src 'self'; img-src 'self' data:; style-src 'self' 'unsafe-inline'; script-src 'self'; frame-ancestors 'none'");
        response.setHeader("X-Content-Type-Options", "nosniff");
        response.setHeader("X-Frame-Options", "DENY");
        response.setHeader("Referrer-Policy", "no-referrer");
        response.setHeader("X-XSS-Protection", "1; mode=block");

        chain.doFilter(request, response);
    }

    private boolean matches(String text) {
        for (Pattern p : XSS_PATTERNS) {
            if (p.matcher(text).find()) {
                return true;
            }
        }
        return false;
    }
}
