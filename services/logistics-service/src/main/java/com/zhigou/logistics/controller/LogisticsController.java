package com.zhigou.logistics.controller;

import com.zhigou.common.Result;
import com.zhigou.logistics.entity.TrackEvent;
import com.zhigou.logistics.service.impl.LogisticsServiceImpl;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController @RequestMapping(produces = "application/json") @RequiredArgsConstructor
public class LogisticsController {
    private final LogisticsServiceImpl service;

    @PostMapping("/freight/calculate")
    public Result<Map<String, Object>> calculate(@RequestBody Map<String, Object> body) {
        long totalAmount = ((Number) body.get("totalAmount")).longValue();
        int weightG = ((Number) body.getOrDefault("weightG", 0)).intValue();
        long fee = service.calculate(totalAmount, weightG);
        return Result.ok(Map.of("freightFee", fee, "freeShipping", fee == 0, "reason", fee == 0 ? "满额包邮" : "按重量计费"));
    }

    @PostMapping("/shipment/create")
    public Result<String> create(@RequestBody Map<String, Object> body) {
        String orderNo = (String) body.get("orderNo");
        Long userId = ((Number) body.get("userId")).longValue();
        String addr = (String) body.getOrDefault("receiverAddr", "{}");
        int weightG = ((Number) body.getOrDefault("weightG", 0)).intValue();
        long freightFee = ((Number) body.getOrDefault("freightFee", 0)).longValue();
        return Result.ok(service.createShipment(orderNo, userId, addr, weightG, freightFee));
    }

    @GetMapping("/shipment/{shipmentNo}/track")
    public Result<List<TrackEvent>> track(@PathVariable String shipmentNo) {
        return Result.ok(service.track(shipmentNo));
    }

    @PostMapping("/shipment/{shipmentNo}/cancel")
    public Result<Void> cancel(@PathVariable String shipmentNo) {
        service.cancel(shipmentNo); return Result.ok();
    }
}