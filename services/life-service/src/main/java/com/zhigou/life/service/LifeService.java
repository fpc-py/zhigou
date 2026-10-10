package com.zhigou.life.service;

import com.zhigou.life.entity.LifeAppointment;
import com.zhigou.life.entity.LifeSku;
import com.zhigou.life.entity.PoiStore;

import java.util.List;
import java.util.Map;

public interface LifeService {
    List<PoiStore> pageStores(String category, int pageNum, int pageSize);
    Map<String, Object> storeDetail(Long id);
    List<LifeSku> pageSkus(String category, int pageNum, int pageSize);
    LifeAppointment createAppointment(Long userId, Long storeId, Long skuId, String appointmentTime, String remark);
    List<LifeAppointment> myAppointments(Long userId);
    LifeAppointment cancelAppointment(Long id, Long userId);
}
