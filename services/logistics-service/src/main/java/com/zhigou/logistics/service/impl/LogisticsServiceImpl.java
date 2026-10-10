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
    private final DispatchActionMapper dispatchMapper;
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

    /**
     * 物流延误预警扫描：在途/待揽收（未签收、未取消）运单，按最近轨迹节点时间判定疑似停滞。
     * 阈值 stagnantHours（默认 48h）：最新节点早于阈值或完全无轨迹 → 标记风险。
     */
    public List<java.util.Map<String, Object>> delayAlerts(int stagnantHours) {
        if (stagnantHours <= 0) stagnantHours = 48;
        java.time.LocalDateTime deadline = java.time.LocalDateTime.now().minusHours(stagnantHours);
        List<Shipment> active = shipmentMapper.selectList(new LambdaQueryWrapper<Shipment>()
                .notIn(Shipment::getStatus, "DELIVERED", "CANCELED", "CANCELING"));
        List<java.util.Map<String, Object>> alerts = new java.util.ArrayList<>();
        for (Shipment s : active) {
            List<TrackEvent> evs = trackMapper.selectList(new LambdaQueryWrapper<TrackEvent>()
                    .eq(TrackEvent::getShipmentNo, s.getShipmentNo()).orderByDesc(TrackEvent::getNodeTime));
            boolean stagnant;
            String latestNode = "无轨迹记录";
            java.time.LocalDateTime latestTime = null;
            if (evs.isEmpty()) {
                try { evs = carrierClient.queryTrack(s.getShipmentNo()); } catch (Exception ignored) {}
            }
            for (TrackEvent e : evs) {
                if (e.getNodeTime() == null) continue;
                if (latestTime == null || e.getNodeTime().isAfter(latestTime)) {
                    latestTime = e.getNodeTime();
                    latestNode = e.getNodeName() == null ? (e.getDescription() == null ? "已揽收" : e.getDescription()) : e.getNodeName();
                }
            }
            stagnant = latestTime == null || latestTime.isBefore(deadline);
            java.util.Map<String, Object> m = new java.util.HashMap<>();
            m.put("shipmentNo", s.getShipmentNo());
            m.put("orderNo", s.getOrderNo());
            m.put("userId", String.valueOf(s.getUserId()));
            m.put("status", s.getStatus());
            m.put("carrier", s.getCarrier());
            m.put("latestNode", latestNode);
            m.put("latestTime", latestTime == null ? null : latestTime.toString());
            m.put("stagnant", stagnant);
            m.put("hint", stagnant ? "疑似停滞/延误，建议催件或更换配送方式" : "运输正常，无需干预");
            alerts.add(m);
        }
        alerts.sort((a, b) -> Boolean.compare(!(Boolean) a.get("stagnant"), !(Boolean) b.get("stagnant")));
        log.info("延误预警扫描: 在途 {} 单, 疑似停滞 {} 单", active.size(), alerts.stream().filter(a -> (Boolean) a.get("stagnant")).count());
        return alerts;
    }

    /** 一键调度：对运单写调度动作记录（URGE/REDELIVER/SELF_PICKUP/CHANGE_ADDRESS/RETURN，演示口径直接 DONE） */
    @Transactional
    public DispatchAction dispatch(String shipmentNo, String action, String reason) {
        if (shipmentNo == null || shipmentNo.isBlank()) throw new BizException(40040, "未指定运单号");
        java.util.Set<String> allowed = java.util.Set.of("URGE", "REDELIVER", "SELF_PICKUP", "CHANGE_ADDRESS", "RETURN");
        if (!allowed.contains(action)) throw new BizException(40041, "不支持的调度动作: " + action);
        Shipment s = shipmentMapper.selectOne(new LambdaQueryWrapper<Shipment>().eq(Shipment::getShipmentNo, shipmentNo));
        if (s == null) throw new BizException(404, "运单不存在: " + shipmentNo);
        DispatchAction d = new DispatchAction();
        d.setShipmentNo(shipmentNo);
        d.setAction(action);
        d.setReason(reason == null || reason.isBlank() ? defaultDispatchReason(action) : reason);
        d.setStatus("DONE");
        dispatchMapper.insert(d);
        log.info("物流调度: shipmentNo={}, action={}", shipmentNo, action);
        return d;
    }

    private String defaultDispatchReason(String action) {
        switch (action) {
            case "URGE": return "联系承运商催件（演示口径）";
            case "REDELIVER": return "重新派送：预约下次配送时间";
            case "SELF_PICKUP": return "改为自提：前往就近网点取件";
            case "CHANGE_ADDRESS": return "修改收货地址：已通知承运商";
            case "RETURN": return "退换货：发起原路退回/换货";
            default: return "物流调度";
        }
    }
}