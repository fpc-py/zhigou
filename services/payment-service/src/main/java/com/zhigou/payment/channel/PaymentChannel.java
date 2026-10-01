package com.zhigou.payment.channel;

/** 支付渠道抽象接口——预留微信/支付宝替换点 */
public interface PaymentChannel {
    String createPayment(String orderNo, Long amount);
    boolean verifyCallback(String paymentNo, String sign, String body);
}