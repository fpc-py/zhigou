package com.zhigou.aftersale.client;

import com.zhigou.common.Result;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.Map;

/**
 * 支付服务客户端（售后退款资金流）。
 * payment-service 退款接口幂等：同订单重复退款只成功一次。
 */
@FeignClient(name = "payment-service", url = "${payment-service.url}")
public interface PaymentClient {

    @PostMapping("/payment/refund")
    Result<Map<String, String>> refund(@RequestBody Map<String, Object> body);
}
