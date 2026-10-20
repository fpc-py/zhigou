package com.zhigou.common.crypto;

import lombok.extern.slf4j.Slf4j;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * B5 金额 AES-GCM 加密工具。
 *
 * <p>密文格式：`ENC:{base64(iv || ciphertext+tag)}`；无 `ENC:` 前缀的存量明文原样返回（灰度兼容）。</p>
 *
 * <p>密钥来自配置 `zhigou.crypto.key`（或环境变量 ZHIGOU_CRYPTO_KEY，≥16 字节）；
 * 未配置时降级为明文透传并 warn——本地/测试环境不阻断，生产必须配置。</p>
 *
 * <p>与手机号 AES/ECB（确定性、可查询）不同：金额列不需要按值查询/聚合（聚合在应用层计算），
 * 采用 GCM 随机 IV 更安全；这也是面试可深聊的取舍点。</p>
 */
@Slf4j
public final class AmountEncryptUtil {

    private static final String PREFIX = "ENC:";
    private static final String TRANSFORMATION = "AES/GCM/NoPadding";
    private static final int TAG_BITS = 128;
    private static final int IV_LEN = 12;
    private static volatile String keySecret;

    private AmountEncryptUtil() { }

    public static void init(String secret) {
        keySecret = secret;
    }

    private static boolean enabled() {
        return keySecret != null && keySecret.length() >= 16;
    }

    public static String encrypt(String plainText) {
        if (!enabled()) {
            log.warn("金额加密密钥未配置（zhigou.crypto.key），金额明文透传——生产环境必须配置！");
            return plainText;
        }
        try {
            byte[] iv = new byte[IV_LEN];
            new SecureRandom().nextBytes(iv);
            SecretKeySpec key = buildKey(keySecret);
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, iv));
            byte[] ct = cipher.doFinal(plainText.getBytes(StandardCharsets.UTF_8));
            byte[] out = new byte[IV_LEN + ct.length];
            System.arraycopy(iv, 0, out, 0, IV_LEN);
            System.arraycopy(ct, 0, out, IV_LEN, ct.length);
            return PREFIX + Base64.getEncoder().encodeToString(out);
        } catch (Exception e) {
            throw new RuntimeException("金额加密失败", e);
        }
    }

    public static String decrypt(String cipherText) {
        if (cipherText == null) return null;
        if (!cipherText.startsWith(PREFIX)) {
            // 存量明文（灰度兼容：增量加密、存量回填前按明文读取）
            return cipherText;
        }
        if (!enabled()) {
            log.warn("金额加密密钥未配置，无法解密已加密金额");
            return cipherText;
        }
        try {
            byte[] raw = Base64.getDecoder().decode(cipherText.substring(PREFIX.length()));
            byte[] iv = new byte[IV_LEN];
            System.arraycopy(raw, 0, iv, 0, IV_LEN);
            byte[] ct = new byte[raw.length - IV_LEN];
            System.arraycopy(raw, IV_LEN, ct, 0, ct.length);
            SecretKeySpec key = buildKey(keySecret);
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, iv));
            return new String(cipher.doFinal(ct), StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new RuntimeException("金额解密失败（密钥不一致或数据损坏）", e);
        }
    }

    private static SecretKeySpec buildKey(String secret) {
        byte[] keyBytes = new byte[16];
        byte[] secretBytes = secret.getBytes(StandardCharsets.UTF_8);
        System.arraycopy(secretBytes, 0, keyBytes, 0, Math.min(secretBytes.length, 16));
        return new SecretKeySpec(keyBytes, "AES");
    }
}
