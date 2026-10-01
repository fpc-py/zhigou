package com.zhigou.payment.channel;

import cn.hutool.crypto.digest.DigestUtil;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class SandboxChannel implements PaymentChannel {

    @Value("${payment.sandbox-secret}") private String secret;

    @Override public String createPayment(String orderNo, Long amount) {
        return "https://sandbox.pay.zhigou.com/qr/" + orderNo + "?amount=" + amount;
    }

    @Override public boolean verifyCallback(String paymentNo, String sign, String body) {
        return DigestUtil.sha256Hex(paymentNo + secret).equals(sign);
    }
}