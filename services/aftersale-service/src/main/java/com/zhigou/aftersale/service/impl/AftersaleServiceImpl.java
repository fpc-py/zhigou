package com.zhigou.aftersale.service.impl;

import cn.hutool.core.util.IdUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zhigou.aftersale.client.InventoryClient;
import com.zhigou.aftersale.client.OrderClient;
import com.zhigou.aftersale.client.PaymentClient;
import com.zhigou.aftersale.dto.ApplyRequest;
import com.zhigou.aftersale.dto.OrderInfo;
import com.zhigou.aftersale.entity.AftersaleOrder;
import com.zhigou.aftersale.entity.RepairAppointment;
import com.zhigou.aftersale.entity.WarrantyInfo;
import com.zhigou.aftersale.mapper.AftersaleOrderMapper;
import com.zhigou.aftersale.mapper.RepairAppointmentMapper;
import com.zhigou.aftersale.mapper.WarrantyInfoMapper;
import com.zhigou.aftersale.state.AftersaleState;
import com.zhigou.common.BizException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Slf4j @Service @RequiredArgsConstructor
public class AftersaleServiceImpl {

    private final AftersaleOrderMapper mapper;
    private final OrderClient orderClient;
    private final PaymentClient paymentClient;
    private final InventoryClient inventoryClient;
    private final WarrantyInfoMapper warrantyMapper;
    private final RepairAppointmentMapper repairMapper;

    private static final Set<String> ALLOWED_ORDER_STATUS = Set.of("PAID", "SHIPPED", "COMPLETED");
    private static final Set<String> REFUNDABLE_STATUS = Set.of("SELLER_APPROVED", "REFUNDING");

    @Transactional
    public AftersaleOrder apply(Long userId, ApplyRequest req) {
        // 1. 同订单未完结售后单不允许再次申请
        long activeCount = mapper.selectCount(new LambdaQueryWrapper<AftersaleOrder>()
                .eq(AftersaleOrder::getOrderNo, req.getOrderNo())
                .notIn(AftersaleOrder::getStatus, "REFUNDED", "REJECTED", "CANCELED"));
        if (activeCount > 0) throw new BizException(409, "该订单已有进行中的售后单");

        // 2. 调用 order-service 校验
        OrderInfo order = orderClient.getOrder(Long.valueOf(req.getOrderNo())).getData();
        if (order == null) throw new BizException(404, "订单不存在");
        if (!order.getUserId().equals(userId)) throw new BizException(403, "无权操作该订单");
        if (!ALLOWED_ORDER_STATUS.contains(order.getOrderStatus()))
            throw new BizException(400, "当前订单状态不允许申请售后");
        if (req.getAmount() > order.getPayAmount()) throw new BizException(400, "退款金额超过实付");

        // 3. 创建售后单
        AftersaleOrder ao = new AftersaleOrder();
        String no = "AS" + IdUtil.fastSimpleUUID().toUpperCase().substring(0, 16);
        ao.setAftersaleNo(no); ao.setOrderNo(req.getOrderNo()); ao.setUserId(userId);
        ao.setType(req.getType()); ao.setReason(req.getReason());
        ao.setAmount(req.getAmount()); ao.setStatus("APPLYING");
        ao.setSkuId(req.getSkuId()); ao.setCount(req.getCount());
        if (req.getImages() != null) ao.setImages(String.join(",", req.getImages()));
        ao.setApplyAt(LocalDateTime.now());
        mapper.insert(ao);
        log.info("售后申请: aftersaleNo={}, orderNo={}", no, req.getOrderNo());
        return ao;
    }


    /** 质保提醒：扫描用户质保信息，30 天内到期 EXPIRING_SOON / 已过期 EXPIRED / 正常 NORMAL */
    public List<Map<String, Object>> warrantyAlerts(Long userId, int days) {
        if (days <= 0) days = 30;
        java.time.LocalDateTime now = java.time.LocalDateTime.now();
        List<WarrantyInfo> list = warrantyMapper.selectList(new LambdaQueryWrapper<WarrantyInfo>().eq(WarrantyInfo::getUserId, userId));
        List<Map<String, Object>> alerts = new java.util.ArrayList<>();
        for (WarrantyInfo w : list) {
            long daysLeft = java.time.Duration.between(now, w.getExpireTime()).toDays();
            String status = w.getExpireTime().isBefore(now) ? "EXPIRED" : (daysLeft <= days ? "EXPIRING_SOON" : "NORMAL");
            String hint;
            if (status.equals("EXPIRED")) hint = "质保已过期，建议关注维修/以旧换新方案";
            else if (status.equals("EXPIRING_SOON")) hint = "质保即将到期（剩 " + daysLeft + " 天），有问题请尽快申请售后";
            else hint = "质保期内，正常保障";
            Map<String, Object> m = new java.util.HashMap<>();
            m.put("orderNo", w.getOrderNo()); m.put("skuId", w.getSkuId());
            m.put("productName", w.getProductName());
            m.put("purchaseTime", w.getPurchaseTime().toString());
            m.put("expireTime", w.getExpireTime().toString());
            m.put("daysLeft", daysLeft);
            m.put("status", status); m.put("hint", hint);
            alerts.add(m);
        }
        alerts.sort((a, b) -> Boolean.compare(!a.get("status").equals("EXPIRED") && !a.get("status").equals("EXPIRING_SOON"),
                !b.get("status").equals("EXPIRED") && !b.get("status").equals("EXPIRING_SOON")));
        log.info("质保提醒扫描: userId={}, {} 条, 风险 {} 条", userId, alerts.size(),
                alerts.stream().filter(a -> !a.get("status").equals("NORMAL")).count());
        return alerts;
    }

    /** 维修预约：创建预约单（演示口径直接 PENDING，正式版需商家确认排期 + 到店/寄修） */
    @Transactional
    public RepairAppointment createRepairAppointment(Long userId, Map<String, Object> req) {
        String orderNo = req.get("orderNo") == null ? null : String.valueOf(req.get("orderNo"));
        String faultDesc = req.get("faultDesc") == null ? null : String.valueOf(req.get("faultDesc"));
        String appointmentTimeStr = req.get("appointmentTime") == null ? null : String.valueOf(req.get("appointmentTime"));
        if (orderNo == null || orderNo.isBlank()) throw new BizException(40050, "未指定订单号");
        if (faultDesc == null || faultDesc.isBlank()) throw new BizException(40051, "请描述故障问题");
        if (appointmentTimeStr == null || appointmentTimeStr.isBlank()) throw new BizException(40052, "请选择期望维修时间");
        RepairAppointment r = new RepairAppointment();
        r.setAppointmentNo("RP" + cn.hutool.core.util.IdUtil.fastSimpleUUID().toUpperCase().substring(0, 14));
        r.setOrderNo(orderNo);
        r.setSkuId(req.get("skuId") == null ? null : String.valueOf(req.get("skuId")));
        r.setUserId(userId);
        r.setProductName(req.get("productName") == null ? null : String.valueOf(req.get("productName")));
        r.setFaultDesc(faultDesc);
        r.setContactPhone(req.get("contactPhone") == null ? null : String.valueOf(req.get("contactPhone")));
        r.setAppointmentTime(java.time.LocalDateTime.parse(appointmentTimeStr.substring(0, 19)));
        r.setStatus("PENDING");
        repairMapper.insert(r);
        log.info("维修预约: appointmentNo={}, orderNo={}, user={}", r.getAppointmentNo(), orderNo, userId);
        return r;
    }

    /** 我的维修预约（倒序） */
    public List<RepairAppointment> repairAppointments(Long userId) {
        return repairMapper.selectList(new LambdaQueryWrapper<RepairAppointment>()
                .eq(RepairAppointment::getUserId, userId).orderByDesc(RepairAppointment::getId));
    }

    public List<AftersaleOrder> mine(Long userId) {
        return mapper.selectList(new LambdaQueryWrapper<AftersaleOrder>().eq(AftersaleOrder::getUserId, userId));
    }

    public AftersaleOrder detail(String no) { return mapper.selectOne(new LambdaQueryWrapper<AftersaleOrder>().eq(AftersaleOrder::getAftersaleNo, no)); }

    @Transactional
    public void cancel(Long userId, String no) {
        AftersaleOrder ao = requireOwn(userId, no);
        AftersaleState.validate(AftersaleState.from(ao.getStatus()), AftersaleState.CANCELED);
        ao.setStatus("CANCELED"); ao.setFinishAt(LocalDateTime.now()); mapper.updateById(ao);
    }

    @Transactional
    public void approve(String no) {
        AftersaleOrder ao = mapper.selectOne(new LambdaQueryWrapper<AftersaleOrder>().eq(AftersaleOrder::getAftersaleNo, no));
        if (ao == null) throw new BizException(404, "售后单不存在");
        AftersaleState.validate(AftersaleState.from(ao.getStatus()), AftersaleState.SELLER_APPROVED);
        ao.setStatus("SELLER_APPROVED"); mapper.updateById(ao);
    }

    @Transactional
    public void reject(String no, String reason) {
        AftersaleOrder ao = mapper.selectOne(new LambdaQueryWrapper<AftersaleOrder>().eq(AftersaleOrder::getAftersaleNo, no));
        if (ao == null) throw new BizException(404, "售后单不存在");
        AftersaleState.validate(AftersaleState.from(ao.getStatus()), AftersaleState.REJECTED);
        ao.setStatus("REJECTED"); ao.setRejectReason(reason); ao.setFinishAt(LocalDateTime.now());
        mapper.updateById(ao);
    }

    @Transactional
    public void onRefundSuccess(String aftersaleNo) {
        AftersaleOrder ao = mapper.selectOne(new LambdaQueryWrapper<AftersaleOrder>().eq(AftersaleOrder::getAftersaleNo, aftersaleNo));
        if (ao == null) return;
        if ("REFUNDED".equals(ao.getStatus())) return; // 幂等
        AftersaleState.validate(AftersaleState.from(ao.getStatus()), AftersaleState.REFUNDED);
        ao.setStatus("REFUNDED"); ao.setFinishAt(LocalDateTime.now()); mapper.updateById(ao);
    }

    @Transactional
    public void startRefunding(String no) {
        AftersaleOrder ao = mapper.selectOne(new LambdaQueryWrapper<AftersaleOrder>().eq(AftersaleOrder::getAftersaleNo, no));
        if (ao == null) return;
        AftersaleState.validate(AftersaleState.from(ao.getStatus()), AftersaleState.REFUNDING);
        ao.setStatus("REFUNDING"); mapper.updateById(ao);
    }

    /**
     * 退款（真实资金流）：
     * 1. SELLER_APPROVED → REFUNDING（状态机）；
     * 2. 调 payment-service 退款（沙箱即时成功，同订单幂等）；
     * 3. 成功 → REFUNDING → REFUNDED，记录退款单号；
     * 4. 退回库存（按售后单 skuId/count，订单级幂等回滚，失败仅告警不阻断资金流）。
     * 已 REFUNDED 幂等返回；REFUNDING 视为重试（资金侧幂等保证不重复退款）。
     */
    @Transactional
    public String refund(String no) {
        AftersaleOrder ao = mapper.selectOne(new LambdaQueryWrapper<AftersaleOrder>().eq(AftersaleOrder::getAftersaleNo, no));
        if (ao == null) throw new BizException(404, "售后单不存在");
        if ("REFUNDED".equals(ao.getStatus())) return ao.getRefundNo(); // 幂等
        if (!REFUNDABLE_STATUS.contains(ao.getStatus()))
            throw new BizException(40050, "当前状态不可退款: " + ao.getStatus());

        // 1. 状态机：SELLER_APPROVED → REFUNDING（REFUNDING 状态为退款重试，不再重复跃迁）
        if ("SELLER_APPROVED".equals(ao.getStatus())) {
            AftersaleState.validate(AftersaleState.SELLER_APPROVED, AftersaleState.REFUNDING);
            ao.setStatus("REFUNDING"); mapper.updateById(ao);
        }

        // 2. 真实资金流：payment-service 退款（幂等——同订单重复退款只成功一次）
        Map<String, Object> body = new java.util.HashMap<>();
        body.put("orderNo", ao.getOrderNo());
        body.put("amount", ao.getAmount());
        body.put("reason", ao.getReason());
        com.zhigou.common.Result<Map<String, String>> resp = paymentClient.refund(body);
        if (resp == null || resp.getData() == null || resp.getData().get("refundNo") == null)
            throw new BizException(500, "退款渠道返回异常");
        String refundNo = resp.getData().get("refundNo");

        // 3. REFUNDING → REFUNDED（幂等：重复退款/重试时 REFUNDED 直接返回）
        AftersaleState.validate(AftersaleState.REFUNDING, AftersaleState.REFUNDED);
        ao.setStatus("REFUNDED"); ao.setFinishAt(LocalDateTime.now()); ao.setRefundNo(refundNo);
        mapper.updateById(ao);
        log.info("售后退款成功: aftersaleNo={}, orderNo={}, refundNo={}, amount={}", no, ao.getOrderNo(), refundNo, ao.getAmount());

        // 4. 退回库存联动（订单级幂等回滚；失败仅告警，资金流已完成不阻断）
        try {
            if (ao.getSkuId() != null && ao.getCount() != null && ao.getCount() > 0) {
                Map<String, Object> rb = new java.util.HashMap<>();
                rb.put("orderId", ao.getOrderNo());
                rb.put("items", List.of(Map.of("skuId", ao.getSkuId(), "count", ao.getCount())));
                inventoryClient.rollback(rb);
                log.info("售后退回库存: aftersaleNo={}, skuId={}, count={}", no, ao.getSkuId(), ao.getCount());
            }
        } catch (Exception e) {
            log.error("售后退回库存失败（待人工补偿）: aftersaleNo={}, err={}", no, e.getMessage());
        }
        return refundNo;
    }

    private AftersaleOrder requireOwn(Long userId, String no) {
        AftersaleOrder ao = mapper.selectOne(new LambdaQueryWrapper<AftersaleOrder>().eq(AftersaleOrder::getAftersaleNo, no));
        if (ao == null) throw new BizException(404, "售后单不存在");
        if (!ao.getUserId().equals(userId)) throw new BizException(403, "无权操作");
        return ao;
    }
}