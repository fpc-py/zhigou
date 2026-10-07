package com.zhigou.inventory.service;

import com.zhigou.inventory.entity.Stock;

import java.util.List;
import java.util.Map;

public interface InventoryService {
    boolean preDeduct(Long skuId, int count);
    void confirm(Long skuId, int count);
    void rollback(Long skuId, int count);
    /** 按订单整体回滚（幂等：同 orderId 只释放一次，供关单/取消同步调用与 MQ 兜底消费共用） */
    void rollbackOrder(String orderId, List<Map<String, Object>> items);
    void warmUp();
    /** 按 skuId 查询库存（供 AI 导购等下游服务调用） */
    Stock query(Long skuId);
}