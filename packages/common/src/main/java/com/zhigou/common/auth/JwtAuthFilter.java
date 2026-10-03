package com.zhigou.common.auth;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/**
 * JWT 鉴权过滤器。
 *
 * 处理逻辑（按优先级）：
 * 1. 优先检查 X-User-Id header（内网调用，由 BFF 注入）
 * 2. 从 Authorization: Bearer xxx 解析并验证 JWT
 * 3. 有 token 但无效 → 返回 401
 * 4. 无 token → 直接放行（由 SecurityConfig 的 .anyRequest().authenticated() 拦截）
 *
 * 白名单路径（/auth/**、/actuator/health、/doc.html 等）在 SecurityConfig 中
 * 配置为 permitAll()，不会进入认证检查。
 */
public class JwtAuthFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(JwtAuthFilter.class);

    private final String secret;

    public JwtAuthFilter(String secret) {
        this.secret = secret;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res,
                                    FilterChain chain) throws ServletException, IOException {

        String path = req.getRequestURI();
        String authHdr = req.getHeader("Authorization");
        log.info("==JwtAuthFilter IN path={} authHeaderPresent={}", path, authHdr != null);

        try {
            // ── 1. 优先检查内网透传 header ──
            String userIdFromHeader = req.getHeader("X-User-Id");
            if (userIdFromHeader != null && !userIdFromHeader.isBlank()) {
                try {
                    Long userId = Long.parseLong(userIdFromHeader);
                    UserContext.setUserId(userId);
                    setSecurityAuthentication(userId);
                    log.info("==JwtAuthFilter X-User-Id path userId={}", userId);
                } catch (NumberFormatException e) {
                    log.warn("X-User-Id 格式异常: {}", userIdFromHeader);
                }
                chain.doFilter(req, res);
                return;
            }

            // ── 2. 从 Authorization header 解析 ──
            if (authHdr != null && authHdr.startsWith("Bearer ")) {
                String token = authHdr.substring(7);
                Long userId = JwtTokenUtil.verifyToken(token, secret);
                log.info("==JwtAuthFilter verifyToken result userId={}", userId);
                if (userId != null) {
                    UserContext.setUserId(userId);
                    setSecurityAuthentication(userId);
                    log.info("==JwtAuthFilter auth set in context: {}",
                            SecurityContextHolder.getContext().getAuthentication());
                } else {
                    // 有 token 但不是有效 → 401，不抛栈
                    write401(res, "Token 无效或已过期");
                    return;
                }
            }

            // ── 3. 无 token 或 token 有效 → 放行（SecurityConfig 控制白名单和认证） ──
            chain.doFilter(req, res);
        } finally {
            UserContext.clear();
            SecurityContextHolder.clearContext();
        }
    }

    /**
     * 将 userId 注入 Spring Security 上下文，
     * 使 SecurityConfig 的 .anyRequest().authenticated() 放行。
     */
    private void setSecurityAuthentication(Long userId) {
        var authorities = List.of(new SimpleGrantedAuthority("ROLE_USER"));
        var authentication = new UsernamePasswordAuthenticationToken(userId, null, authorities);
        SecurityContextHolder.getContext().setAuthentication(authentication);
    }

    private void write401(HttpServletResponse res, String message) throws IOException {
        res.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        res.setContentType("application/json;charset=UTF-8");
        res.getWriter().write("{\"code\":401,\"message\":\"" + message + "\",\"data\":null}");
    }
}