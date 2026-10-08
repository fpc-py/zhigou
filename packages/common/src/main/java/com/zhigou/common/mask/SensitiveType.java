package com.zhigou.common.mask;

/**
 * 敏感数据类型（日志脱敏分类）。
 */
public enum SensitiveType {

    /** 手机号：138****8001 */
    PHONE,
    /** 身份证：保留前6后4 */
    ID_CARD,
    /** 银行卡：保留前6后4 */
    BANK_CARD,
    /** 邮箱：a***@domain */
    EMAIL,
    /** 密码：全掩 */
    PASSWORD,
    /** Token / Authorization：全掩 */
    TOKEN,
    /** 密钥 / Secret / Key：全掩 */
    SECRET,
    /** 姓名：张* */
    NAME,
    /** 地址：保留省市 */
    ADDRESS,
    /** 默认：按长度智能掩码 */
    DEFAULT
}
