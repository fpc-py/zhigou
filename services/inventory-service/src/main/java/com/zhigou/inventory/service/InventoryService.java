package com.zhigou.inventory.service;

import com.zhigou.inventory.entity.Stock;

public interface InventoryService {
    boolean preDeduct(Long skuId, int count);
    void confirm(Long skuId, int count);
    void rollback(Long skuId, int count);
    void warmUp();
    /** 按 skuId 查询库存（供 AI 导购等下游服务调用） */
    Stock query(Long skuId);
}