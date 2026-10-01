package com.zhigou.inventory.service;

public interface InventoryService {
    boolean preDeduct(Long skuId, int count);
    void confirm(Long skuId, int count);
    void rollback(Long skuId, int count);
    void warmUp();
}