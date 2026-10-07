package com.zhigou.payment.config;

import com.zhigou.common.auth.JwtAuthFilter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.context.annotation.Profile;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.intercept.AuthorizationFilter;

@Configuration
@EnableWebSecurity
@Profile("!test")
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http, JwtAuthFilter jwtAuthFilter) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(
                                "/auth/**", "/actuator/health", "/actuator/health/**",
                                "/doc.html", "/swagger-ui.html", "/swagger-ui/**",
                                "/v3/api-docs/**", "/webjars/**",
                                // 支付沙箱回调与未来真实渠道回调：服务端回调无用户上下文，靠签名验签而非登录态
                                "/payment/sandbox/mock-pay", "/payment/notify/**",
                                // 运维手动对账入口（生产应加内网白名单/管理员鉴权）
                                "/payment/reconcile"
                        ).permitAll()
                        .anyRequest().authenticated()
                )
                .addFilterBefore(jwtAuthFilter, AuthorizationFilter.class);
        return http.build();
    }

    @Bean
    public JwtAuthFilter jwtAuthFilter(@Value("${jwt.secret}") String secret) {
        return new JwtAuthFilter(secret);
    }
}