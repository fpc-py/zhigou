package com.zhigou.auth.util;

import com.nimbusds.jose.*;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jose.crypto.MACVerifier;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.zhigou.auth.config.JwtProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.text.ParseException;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class JwtUtil {

    private final JwtProperties jwtProperties;

    /**
     * 签发 accessToken
     */
    public String createAccessToken(Long userId, String phone, String role, Long merchantId) {
        return sign(userId, phone, role, merchantId, jwtProperties.getAccessExpireSeconds());
    }

    /**
     * 签发 refreshToken
     */
    public String createRefreshToken(Long userId, String phone, String role, Long merchantId) {
        return sign(userId, phone, role, merchantId, jwtProperties.getRefreshExpireSeconds());
    }

    /**
     * 解析并校验 JWT，返回 claims
     */
    public JWTClaimsSet parse(String token) throws ParseException, JOSEException {
        SignedJWT signedJWT = SignedJWT.parse(token);
        JWSVerifier verifier = new MACVerifier(jwtProperties.getSecret().getBytes());
        if (!signedJWT.verify(verifier)) {
            throw new JOSEException("JWT 签名校验失败");
        }
        JWTClaimsSet claims = signedJWT.getJWTClaimsSet();
        if (claims.getExpirationTime().before(new Date())) {
            throw new JOSEException("JWT 已过期");
        }
        return claims;
    }

    private String sign(Long userId, String phone, String role, Long merchantId, long expireSeconds) {
        try {
            JWSSigner signer = new MACSigner(jwtProperties.getSecret().getBytes());
            JWTClaimsSet.Builder builder = new JWTClaimsSet.Builder()
                    .subject(String.valueOf(userId))
                    .claim("phone", phone)
                    .claim("role", role)
                    .jwtID(UUID.randomUUID().toString())
                    .issueTime(new Date())
                    .expirationTime(Date.from(Instant.now().plusSeconds(expireSeconds)));
            // 商家账号携带 merchantId claim（多商家隔离：BFF 据此解析归属商家）
            if (merchantId != null) {
                builder.claim("merchantId", merchantId);
            }
            JWTClaimsSet claimsSet = builder.build();
            SignedJWT signedJWT = new SignedJWT(
                    new JWSHeader(JWSAlgorithm.HS256),
                    claimsSet
            );
            signedJWT.sign(signer);
            return signedJWT.serialize();
        } catch (JOSEException e) {
            log.error("JWT 签发失败: userId={}", userId, e);
            throw new RuntimeException("JWT 签发失败", e);
        }
    }
}