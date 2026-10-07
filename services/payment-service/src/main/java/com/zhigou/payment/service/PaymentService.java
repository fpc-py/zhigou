package com.zhigou.payment.service;

public interface PaymentService {
    String create(Long userId, String orderNo, Long amount);
    void mockPay(String paymentNo, String sign);
    /** 退款：按订单号退款，幂等（同订单重复退款只成功一次），沙箱即时成功。返回退款单号 */
    String refund(String orderNo, Long amount, String reason);
    void reconcile();
}