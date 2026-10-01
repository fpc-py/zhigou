package com.zhigou.logistics.client;

import com.zhigou.logistics.entity.TrackEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j @Component
public class SandboxCarrierClient implements CarrierClient {

    @Override
    public String createOrder(String orderNo, String receiverAddr, int weightG) {
        log.info("沙箱创建运单: orderNo={}, weightG={}", orderNo, weightG);
        return "SF" + java.time.LocalDate.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd"));
    }

    @Override
    public List<TrackEvent> queryTrack(String shipmentNo) {
        LocalDateTime now = LocalDateTime.now();
        return List.of(
                evt(shipmentNo, now.minusHours(4), "已揽收", "快递员已揽收快件"),
                evt(shipmentNo, now.minusHours(2), "运输中", "快件在运输途中"),
                evt(shipmentNo, now.minusHours(1), "派送中", "快递员正在派送"),
                evt(shipmentNo, now.plusHours(2), "已签收", "本人签收"),
                evt(shipmentNo, now.plusHours(3), "评价", "感谢使用智购物流")
        );
    }

    @Override
    public void cancelOrder(String shipmentNo) {
        log.info("沙箱取消运单: shipmentNo={}", shipmentNo);
    }

    private TrackEvent evt(String no, LocalDateTime time, String name, String desc) {
        TrackEvent e = new TrackEvent();
        e.setShipmentNo(no); e.setNodeTime(time); e.setNodeName(name); e.setDescription(desc);
        return e;
    }
}