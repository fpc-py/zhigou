package com.zhigou.logistics.client;

import com.zhigou.logistics.entity.TrackEvent;
import java.util.List;

/**
 * 运输商接口——未来换顺丰/京东 SDK 时实现此接口即可。
 */
public interface CarrierClient {

    /** 创建运单，返回运单号 */
    String createOrder(String orderNo, String receiverAddr, int weightG);

    /** 查询轨迹 */
    List<TrackEvent> queryTrack(String shipmentNo);

    /** 取消运单 */
    void cancelOrder(String shipmentNo);
}