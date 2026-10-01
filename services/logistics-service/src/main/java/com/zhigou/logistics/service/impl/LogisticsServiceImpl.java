package com.zhigou.logistics.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zhigou.common.BizException;
import com.zhigou.logistics.client.CarrierClient;
import com.zhigou.logistics.entity.*;
import com.zhigou.logistics.mapper.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Slf4j @Service @RequiredArgsConstructor
public class LogisticsServiceImpl {

    private final FreightTemplateMapper freightMapper;
    private final ShipmentMapper shipmentMapper;
    private final TrackEventMapper trackMapper;
    private final CarrierClient carrierClient;
    private final StringRedisTemplate redis;

    public long calculate(Long totalAmount, int weightG) {
        FreightTemplate t = freightMapper.selectById(1L);
        if (t == null) return 0;
        if (t.getFreeThresholdAmount() != null && totalAmount >= t.getFreeThresholdAmount()) return 0;

        if (weightG <= t.getFirstWeightG()) return t.getFirstFee();
        int extra = (weightG - t.getFirstWeightG() + t.getContinuedWeightG() - 1) / t.getContinuedWeightG();
        return t.getFirstFee() + (long) extra * t.getContinuedFee();
    }

    @Transactional
    public String createShipment(String orderNo, Long userId, String receiverAddr, int weightG, long freightFee) {
        Shipment existing = shipmentMapper.selectOne(new LambdaQueryWrapper<Shipment>().eq(Shipment::getOrderNo, orderNo));
        if (existing != null) return existing.getShipmentNo(); // 幂等

        String date = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        Long seq = redis.opsForValue().increment("shipment:seq:" + date);
        String shipmentNo = "SF" + date + String.format("%010d", seq);

        Shipment s = new Shipment();
        s.setShipmentNo(shipmentNo); s.setOrderNo(orderNo); s.setUserId(userId);
        s.setReceiverAddr(receiverAddr); s.setWeightG(weightG); s.setFreightFee(freightFee);
        s.setCarrier("SELF"); s.setStatus("CREATED");
        shipmentMapper.insert(s);

        carrierClient.createOrder(orderNo, receiverAddr, weightG);
        log.info("运单创建: shipmentNo={}, orderNo={}", shipmentNo, orderNo);
        return shipmentNo;
    }

    @Transactional
    public void cancel(String orderNo) {
        Shipment s = shipmentMapper.selectOne(new LambdaQueryWrapper<Shipment>().eq(Shipment::getOrderNo, orderNo));
        if (s == null) return;
        if ("CANCELED".equals(s.getStatus())) return;
        s.setStatus("CANCELED"); shipmentMapper.updateById(s);
        carrierClient.cancelOrder(s.getShipmentNo());
        log.info("运单取消: shipmentNo={}", s.getShipmentNo());
    }

    public List<TrackEvent> track(String shipmentNo) {
        List<TrackEvent> dbTrack = trackMapper.selectList(new LambdaQueryWrapper<TrackEvent>().eq(TrackEvent::getShipmentNo, shipmentNo));
        if (!dbTrack.isEmpty()) return dbTrack;
        // 无 DB 记录时走沙箱
        return carrierClient.queryTrack(shipmentNo);
    }
}