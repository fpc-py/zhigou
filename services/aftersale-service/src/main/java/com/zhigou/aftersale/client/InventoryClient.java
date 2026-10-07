package com.zhigou.aftersale.client;

import com.zhigou.common.Result;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.Map;

/**
 * 库存服务客户端（售后退款成功 → 退回库存，订单级幂等回滚）。
 */
@FeignClient(name = "inventory-service", url = "${inventory-service.url}")
public interface InventoryClient {

    @PostMapping("/inventory/rollback")
    Result<Void> rollback(@RequestBody Map<String, Object> body);
}
