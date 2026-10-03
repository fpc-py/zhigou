package com.zhigou.aftersale.service.impl;

import cn.hutool.core.util.IdUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zhigou.aftersale.client.OrderClient;
import com.zhigou.aftersale.dto.ApplyRequest;
import com.zhigou.aftersale.dto.OrderInfo;
import com.zhigou.aftersale.entity.AftersaleOrder;
import com.zhigou.aftersale.mapper.AftersaleOrderMapper;
import com.zhigou.aftersale.state.AftersaleState;
import com.zhigou.common.BizException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

@Slf4j @Service @RequiredArgsConstructor
public class AftersaleServiceImpl {

    private final AftersaleOrderMapper mapper;
    private final OrderClient orderClient;

    private static final Set<String> ALLOWED_ORDER_STATUS = Set.of("PAID", "SHIPPED", "COMPLETED");

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
        if (req.getImages() != null) ao.setImages(String.join(",", req.getImages()));
        ao.setApplyAt(LocalDateTime.now());
        mapper.insert(ao);
        log.info("售后申请: aftersaleNo={}, orderNo={}", no, req.getOrderNo());
        return ao;
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

    private AftersaleOrder requireOwn(Long userId, String no) {
        AftersaleOrder ao = mapper.selectOne(new LambdaQueryWrapper<AftersaleOrder>().eq(AftersaleOrder::getAftersaleNo, no));
        if (ao == null) throw new BizException(404, "售后单不存在");
        if (!ao.getUserId().equals(userId)) throw new BizException(403, "无权操作");
        return ao;
    }
}