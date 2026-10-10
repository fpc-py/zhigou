package com.zhigou.life.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zhigou.common.BizException;
import com.zhigou.life.entity.LifeAppointment;
import com.zhigou.life.entity.LifeSku;
import com.zhigou.life.entity.PoiStore;
import com.zhigou.life.mapper.LifeAppointmentMapper;
import com.zhigou.life.mapper.LifeSkuMapper;
import com.zhigou.life.mapper.PoiStoreMapper;
import com.zhigou.life.service.LifeService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class LifeServiceImpl implements LifeService {

    private final PoiStoreMapper storeMapper;
    private final LifeSkuMapper skuMapper;
    private final LifeAppointmentMapper appointmentMapper;

    @Override
    public List<PoiStore> pageStores(String category, int pageNum, int pageSize) {
        LambdaQueryWrapper<PoiStore> q = new LambdaQueryWrapper<>();
        if (category != null && !category.isBlank()) q.eq(PoiStore::getCategory, category);
        q.orderByDesc(PoiStore::getRating).last("limit " + Math.max(0, (pageNum - 1) * pageSize) + "," + pageSize);
        return storeMapper.selectList(q);
    }

    @Override
    public Map<String, Object> storeDetail(Long id) {
        PoiStore store = storeMapper.selectById(id);
        if (store == null) throw new BizException(40011, "门店不存在");
        List<LifeSku> skus = skuMapper.selectList(new LambdaQueryWrapper<LifeSku>().eq(LifeSku::getStoreId, id));
        Map<String, Object> r = new HashMap<>();
        r.put("store", store);
        r.put("skus", skus);
        return r;
    }

    @Override
    public List<LifeSku> pageSkus(String category, int pageNum, int pageSize) {
        LambdaQueryWrapper<LifeSku> q = new LambdaQueryWrapper<>();
        if (category != null && !category.isBlank()) q.eq(LifeSku::getCategory, category);
        q.orderByDesc(LifeSku::getCreatedAt).last("limit " + Math.max(0, (pageNum - 1) * pageSize) + "," + pageSize);
        return skuMapper.selectList(q);
    }

    @Override
    public LifeAppointment createAppointment(Long userId, Long storeId, Long skuId, String appointmentTime, String remark) {
        if (userId == null) throw new BizException(40001, "请先登录");
        if (storeId == null || storeMapper.selectById(storeId) == null)
            throw new BizException(40011, "门店不存在");
        LifeAppointment a = new LifeAppointment();
        a.setUserId(userId);
        a.setStoreId(storeId);
        a.setSkuId(skuId);
        a.setAppointmentTime(appointmentTime == null || appointmentTime.isBlank() ? LocalDateTime.now().plusHours(2).toString() : appointmentTime);
        a.setRemark(remark);
        a.setStatus(0); // 待确认
        a.setCreatedAt(LocalDateTime.now());
        a.setUpdatedAt(LocalDateTime.now());
        appointmentMapper.insert(a);
        return a;
    }

    @Override
    public List<LifeAppointment> myAppointments(Long userId) {
        if (userId == null) throw new BizException(40001, "请先登录");
        return appointmentMapper.selectList(new LambdaQueryWrapper<LifeAppointment>()
                .eq(LifeAppointment::getUserId, userId)
                .orderByDesc(LifeAppointment::getCreatedAt));
    }

    @Override
    public LifeAppointment cancelAppointment(Long id, Long userId) {
        LifeAppointment a = appointmentMapper.selectById(id);
        if (a == null) throw new BizException(40012, "预约不存在");
        if (!a.getUserId().equals(userId)) throw new BizException(40013, "无权操作该预约");
        if (a.getStatus() == 2) throw new BizException(40014, "已完成的预约不可取消");
        a.setStatus(3);
        a.setUpdatedAt(LocalDateTime.now());
        appointmentMapper.updateById(a);
        return a;
    }
}
