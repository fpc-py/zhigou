package com.zhigou.common.mask;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 日志脱敏工具：不修改业务数据，仅生成用于日志打印的掩码文本。
 *
 * <p>内置正则自动识别：手机号 / 身份证 / 银行卡 / 邮箱；字段名命中敏感词（如
 * password、phone）时整体掩码；其余按 {@link SensitiveType} 显式指定。
 */
public final class MaskUtil {

    private MaskUtil() {
    }

    /** 中国大陆手机号 */
    private static final Pattern PHONE_RE = Pattern.compile("(?<=\\D|^)1[3-9]\\d{9}(?=\\D|$)");
    /** 18 位身份证 */
    private static final Pattern ID_CARD_RE = Pattern.compile("(?<=\\D|^)\\d{17}[\\dXx](?=\\D|$)");
    /** 银行卡（13-19 位数字；以 3/4/5/6 开头，避免误伤 Snowflake 等 2 开头长 ID） */
    private static final Pattern BANK_CARD_RE = Pattern.compile("(?<=\\D|^)[3456]\\d{12,18}(?=\\D|$)");
    /** 邮箱 */
    private static final Pattern EMAIL_RE = Pattern.compile("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}");

    /** 命中即整体掩码的字段名关键词（小写匹配） */
    private static final String[] SENSITIVE_KEYS = {
            "password", "pwd", "secret", "token", "authorization", "accesskey",
            "apikey", "appsecret", "mchkey", "privatekey", "credential", "mobile",
            "phone", "tel", "idcard", "idno", "certno", "bankcard", "cardno",
            "realname", "name", "email", "address"
    };

    /**
     * 智能脱敏：先按值正则（手机号/身份证/邮箱）识别，其余按 DEFAULT 规则。
     */
    public static String mask(String text) {
        if (text == null || text.isEmpty()) {
            return text;
        }
        String s = text;
        Matcher m = EMAIL_RE.matcher(s);
        s = m.replaceAll(mr -> maskEmail(mr.group()));
        m = PHONE_RE.matcher(s);
        s = m.replaceAll(mr -> maskPhone(mr.group()));
        m = ID_CARD_RE.matcher(s);
        s = m.replaceAll(mr -> maskIdCard(mr.group()));
        m = BANK_CARD_RE.matcher(s);
        s = m.replaceAll(mr -> maskBankCard(mr.group()));
        return s;
    }

    /**
     * 按字段名脱敏：key 命中敏感词 → 全掩；否则按值智能识别。
     */
    public static String maskByKey(String key, Object value) {
        if (value == null) {
            return null;
        }
        String v = String.valueOf(value);
        if (v.isEmpty()) {
            return v;
        }
        String lower = key == null ? "" : key.toLowerCase();
        for (String kw : SENSITIVE_KEYS) {
            if (lower.contains(kw)) {
                return allMask(v);
            }
        }
        return mask(v);
    }

    /** 按显式类型脱敏 */
    public static String mask(String text, SensitiveType type) {
        if (text == null || text.isEmpty()) {
            return text;
        }
        return switch (type) {
            case PHONE -> maskPhone(text);
            case ID_CARD -> maskIdCard(text);
            case BANK_CARD -> maskBankCard(text);
            case EMAIL -> maskEmail(text);
            case PASSWORD, TOKEN, SECRET -> allMask(text);
            case NAME -> maskName(text);
            case ADDRESS -> maskAddress(text);
            case DEFAULT -> mask(text);
        };
    }

    public static String maskPhone(String phone) {
        if (phone == null || phone.length() < 7) {
            return allMask(phone);
        }
        return phone.replaceAll("(?<=^\\d{3})\\d+(?=\\d{4}$)", "****");
    }

    public static String maskIdCard(String id) {
        if (id == null || id.length() < 10) {
            return allMask(id);
        }
        return id.replaceAll("(?<=^\\d{6})\\d+(?=\\d{4}$)", "********");
    }

    public static String maskBankCard(String card) {
        if (card == null || card.length() < 10) {
            return allMask(card);
        }
        return card.replaceAll("(?<=^\\d{6})\\d+(?=\\d{4}$)", "******");
    }

    public static String maskEmail(String email) {
        if (email == null || !email.contains("@")) {
            return allMask(email);
        }
        int at = email.indexOf('@');
        String prefix = email.substring(0, at);
        String domain = email.substring(at);
        if (prefix.length() <= 2) {
            return prefix.charAt(0) + "***" + domain;
        }
        return prefix.charAt(0) + "***" + prefix.charAt(prefix.length() - 1) + domain;
    }

    public static String maskName(String name) {
        if (name == null || name.isEmpty()) {
            return name;
        }
        return name.substring(0, 1) + "*".repeat(Math.max(1, name.length() - 1));
    }

    public static String maskAddress(String addr) {
        if (addr == null || addr.length() <= 4) {
            return allMask(addr);
        }
        return addr.substring(0, 4) + "****";
    }

    public static String allMask(String text) {
        if (text == null || text.isEmpty()) {
            return text;
        }
        return "*".repeat(Math.min(text.length(), 8));
    }
}
