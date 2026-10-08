package com.zhigou.common.mask;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class MaskUtilTest {

    @Test
    void maskPhone_keepsHeadTail() {
        assertEquals("138****8001", MaskUtil.mask("13812348001", SensitiveType.PHONE));
    }

    @Test
    void maskPhone_short_fullyMasked() {
        assertEquals("*****", MaskUtil.mask("12345", SensitiveType.PHONE));
    }

    @Test
    void maskIdCard_keeps6and4() {
        assertEquals("110101********1234", MaskUtil.mask("110101199003071234", SensitiveType.ID_CARD));
    }

    @Test
    void maskBankCard_keeps6and4() {
        assertEquals("622202******1234", MaskUtil.mask("62220212345678901234", SensitiveType.BANK_CARD));
    }

    @Test
    void maskEmail_keepsDomain() {
        assertEquals("z***u@example.com", MaskUtil.mask("zhigou@example.com", SensitiveType.EMAIL));
        assertFalse(MaskUtil.mask("zhigou@example.com", SensitiveType.EMAIL).contains("zhigou@example.com"));
    }

    @Test
    void maskName_firstChar() {
        assertEquals("张*", MaskUtil.mask("张", SensitiveType.NAME));
        assertEquals("张**", MaskUtil.mask("张三丰", SensitiveType.NAME));
    }

    @Test
    void allMask_capped8() {
        assertEquals("********", MaskUtil.allMask("super-long-secret-value"));
        assertEquals("***", MaskUtil.allMask("abc"));
    }

    @Test
    void maskByKey_sensitiveKeyFullyMasked() {
        assertEquals("********", MaskUtil.maskByKey("password", "myPass123"));
        assertEquals("********", MaskUtil.maskByKey("Authorization", "Bearer eyJhbGciOi"));
        assertEquals("contact 138****8001", MaskUtil.maskByKey("note", "contact 13812348001"));
    }

    @Test
    void maskByKey_phoneKeyMasksValue() {
        assertEquals("********", MaskUtil.maskByKey("phone", "13812348001"));
    }

    @Test
    void mask_autoDetectInText() {
        String raw = "手机 13812348001，邮箱 zhigou@example.com，证号 110101199003071234";
        String masked = MaskUtil.mask(raw);
        assertFalse(masked.contains("13812348001"));
        assertFalse(masked.contains("zhigou@example.com"));
        assertFalse(masked.contains("110101199003071234"));
        assertNotEquals(raw, masked);
    }

    @Test
    void mask_nullSafe() {
        assertEquals(null, MaskUtil.mask(null));
        assertEquals("", MaskUtil.mask(""));
        assertEquals(null, MaskUtil.maskByKey("phone", null));
    }
}
