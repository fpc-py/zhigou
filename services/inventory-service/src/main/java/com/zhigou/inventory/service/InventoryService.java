package com.zhigou.inventory.service;

import com.zhigou.inventory.entity.ReplenishRecord;
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
    /** 低库存列表：available <= threshold（按余量升序，供商家经营预警） */
    List<Stock> lowStock(int threshold);
    /** 手动补货：给指定 SKU 增加 addQty 库存并写补货记录（演示口径） */
    Stock replenish(Long skuId, int addQty, String remark);
    /** 自动补货：将 available <= threshold 的 SKU 补到 targetQty，返回本次补货明细 */
    List<ReplenishRecord> autoReplenish(int threshold, int targetQty);
    /** 最近补货记录（按时间倒序） */
    List<ReplenishRecord> replenishRecords(int limit);
}