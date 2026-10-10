package com.zhigou.inventory.controller;

import com.zhigou.common.Result;
import com.zhigou.inventory.entity.Stock;
import com.zhigou.inventory.service.InventoryService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController @RequestMapping("/inventory") @RequiredArgsConstructor
public class InventoryController {
    private final InventoryService inventoryService;

    @PostMapping("/preDeduct")
    public Result<Boolean> preDeduct(@RequestBody Map<String, Object> body) {
        Long skuId = ((Number) body.get("skuId")).longValue();
        int count = ((Number) body.get("count")).intValue();
        return Result.ok(inventoryService.preDeduct(skuId, count));
    }

    @PostMapping("/confirm")
    public Result<Void> confirm(@RequestBody Map<String, Object> body) {
        Long skuId = ((Number) body.get("skuId")).longValue();
        int count = ((Number) body.get("count")).intValue();
        inventoryService.confirm(skuId, count); return Result.ok();
    }

    @PostMapping("/rollback")
    public Result<Void> rollback(@RequestBody Map<String, Object> body) {
        // 新版：订单级幂等回滚 {orderId, items:[{skuId,count}]}
        Object orderId = body.get("orderId");
        Object itemsObj = body.get("items");
        if (orderId != null && itemsObj instanceof java.util.List<?> items && !items.isEmpty()) {
            @SuppressWarnings("unchecked")
            java.util.List<Map<String, Object>> itemList = (java.util.List<Map<String, Object>>) items;
            inventoryService.rollbackOrder(String.valueOf(orderId), itemList);
            return Result.ok();
        }
        // 兼容旧版：单条回滚 {skuId, count}
        Long skuId = ((Number) body.get("skuId")).longValue();
        int count = ((Number) body.get("count")).intValue();
        inventoryService.rollback(skuId, count); return Result.ok();
    }

    @GetMapping("/{skuId}")
    public Result<Stock> query(@PathVariable("skuId") Long skuId) {
        return Result.ok(inventoryService.query(skuId));
    }

    @GetMapping("/low-stock")
    public Result<java.util.List<Stock>> lowStock(@RequestParam(defaultValue = "10") int threshold) {
        return Result.ok(inventoryService.lowStock(threshold));
    }
}