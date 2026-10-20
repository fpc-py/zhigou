package com.zhigou.common.crypto;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * B5 金额加解密单元测试：GCM 往返 / 随机 IV / 存量明文兼容 / 无密钥降级。
 */
class AmountEncryptUtilTest {

    @Test
    void encryptDecryptRoundTrip() {
        AmountEncryptUtil.init("zhigou-test-key-0000000000");
        String enc = AmountEncryptUtil.encrypt("29900");
        assertTrue(enc.startsWith("ENC:"), "密文应带 ENC: 前缀");
        assertEquals("29900", AmountEncryptUtil.decrypt(enc));
    }

    @Test
    void randomIvMakesCiphertextDifferent() {
        AmountEncryptUtil.init("zhigou-test-key-0000000000");
        String a = AmountEncryptUtil.encrypt("100");
        String b = AmountEncryptUtil.encrypt("100");
        assertNotEquals(a, b, "GCM 随机 IV 应使同值密文不同（无法按值查询=更安全）");
    }

    @Test
    void legacyPlaintextPassesThrough() {
        AmountEncryptUtil.init("zhigou-test-key-0000000000");
        assertEquals("8800", AmountEncryptUtil.decrypt("8800"), "存量明文（无 ENC: 前缀）应原样返回");
    }

    @Test
    void noKeyDegradesToPlaintextWithWarn() {
        AmountEncryptUtil.init("");
        assertEquals("8800", AmountEncryptUtil.encrypt("8800"), "未配置密钥时明文透传不阻断");
    }
}
