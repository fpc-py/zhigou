package com.zhigou.common.auth;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSVerifier;
import com.nimbusds.jose.crypto.MACVerifier;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.text.ParseException;
import java.util.Date;

/**
 * JWT 验证工具（HS256）。
 * 与 auth-center 的 JwtUtil 使用相同算法和密钥格式。
 * 纯静态方法，无状态，适用于所有服务。
 */
public class JwtTokenUtil {

    private static final Logger log = LoggerFactory.getLogger(JwtTokenUtil.class);

    /**
     * 验证 JWT 并返回 userId（sub 字段）。
     *
     * @param token  JWT 字符串
     * @param secret 共享密钥
     * @return 验证成功返回 userId，失败返回 null
     */
    public static Long verifyToken(String token, String secret) {
        try {
            SignedJWT signedJWT = SignedJWT.parse(token);
            JWSVerifier verifier = new MACVerifier(secret.getBytes());
            if (!signedJWT.verify(verifier)) {
                log.warn("JWT 签名校验失败");
                return null;
            }
            JWTClaimsSet claims = signedJWT.getJWTClaimsSet();
            if (claims.getExpirationTime().before(new Date())) {
                log.warn("JWT 已过期");
                return null;
            }
            String sub = claims.getSubject();
            if (sub == null) {
                log.warn("JWT 缺少 sub");
                return null;
            }
            return Long.parseLong(sub);
        } catch (ParseException | JOSEException | NumberFormatException e) {
            log.warn("JWT 解析失败: {}", e.getMessage());
            return null;
        }
    }
}