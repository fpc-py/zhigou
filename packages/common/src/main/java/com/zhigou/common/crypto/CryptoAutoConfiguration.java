package com.zhigou.common.crypto;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.AutoConfiguration;

import jakarta.annotation.PostConstruct;

/**
 * B5 金额加密密钥初始化：读取 `zhigou.crypto.key`（或环境变量 ZHIGOU_CRYPTO_KEY）。
 */
@AutoConfiguration
public class CryptoAutoConfiguration {

    @Value("${zhigou.crypto.key:${ZHIGOU_CRYPTO_KEY:}}")
    private String cryptoKey;

    @PostConstruct
    public void init() {
        AmountEncryptUtil.init(cryptoKey);
    }
}
