package com.zhigou.payment.service;

public interface PaymentService {
    String create(Long userId, String orderNo, Long amount);
    void mockPay(String paymentNo, String sign);
    void reconcile();
}