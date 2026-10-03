package com.zhigou.aftersale.client;

import com.zhigou.aftersale.dto.OrderInfo;
import com.zhigou.common.Result;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@FeignClient(name = "order-service", url = "${order-service.url}")
public interface OrderClient {
    @GetMapping("/order/{orderId}")
    Result<OrderInfo> getOrder(@PathVariable("orderId") Long orderId);
}