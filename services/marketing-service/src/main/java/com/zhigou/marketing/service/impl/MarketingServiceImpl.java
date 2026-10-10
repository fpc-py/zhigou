package com.zhigou.marketing.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zhigou.common.BizException;
import com.zhigou.marketing.dto.CalculateRequest;
import com.zhigou.marketing.dto.CalculateResponse;
import com.zhigou.marketing.engine.RuleEngine;
import com.zhigou.marketing.entity.*;
import com.zhigou.marketing.mapper.*;
import com.zhigou.marketing.service.CouponService;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;
import java.util.*;

@Slf4j @Service @RequiredArgsConstructor
public class MarketingServiceImpl implements CouponService {

    private final CouponTemplateMapper templateMapper;
    private final UserCouponMapper userCouponMapper;
    private final PromotionRuleMapper ruleMapper;
    private final DiscountSnapshotMapper snapshotMapper;
    private final RuleEngine ruleEngine;

    @PostConstruct void init() { ruleEngine.loadRules(ruleMapper.selectList(null)); }

    @Override @Transactional
    public Long issue(Long userId, Long templateId) {
        CouponTemplate t = templateMapper.selectById(templateId);
        if (t == null || t.getStatus() != 1) throw new BizException(404, "券模板不存在");
        long count = userCouponMapper.selectCount(new LambdaQueryWrapper<UserCoupon>()
                .eq(UserCoupon::getUserId, userId).eq(UserCoupon::getCouponTemplateId, templateId));
        if (count >= t.getPerUserLimit()) throw new BizException(40030, "超过每人限领次数");
        UserCoupon uc = new UserCoupon();
        uc.setUserId(userId); uc.setCouponTemplateId(templateId); uc.setStatus("UNUSED");
        userCouponMapper.insert(uc);
        log.info("发券: userId={}, templateId={}", userId, templateId);
        return uc.getId();
    }

    @Override
    public List<UserCoupon> mine(Long userId, String status) {
        LambdaQueryWrapper<UserCoupon> w = new LambdaQueryWrapper<UserCoupon>().eq(UserCoupon::getUserId, userId);
        if (status != null) w.eq(UserCoupon::getStatus, status);
        return userCouponMapper.selectList(w);
    }

    @Override @Transactional
    public void preFreeze(Long userId, Long couponId, Long orderId) {
        UserCoupon uc = userCouponMapper.selectById(couponId);
        if (uc == null || !uc.getUserId().equals(userId)) throw new BizException(404, "券不存在");
        if (!"UNUSED".equals(uc.getStatus())) throw new BizException(40031, "券状态不可用");
        uc.setStatus("FROZEN"); uc.setSourceOrderId(orderId); uc.setLockedAt(LocalDateTime.now());
        userCouponMapper.updateById(uc);
        log.info("券冻结: couponId={}, orderId={}", couponId, orderId);
    }

    @Override @Transactional
    public void confirm(Long orderId) {
        List<UserCoupon> frozen = userCouponMapper.selectList(new LambdaQueryWrapper<UserCoupon>()
                .eq(UserCoupon::getSourceOrderId, orderId).eq(UserCoupon::getStatus, "FROZEN"));
        for (UserCoupon uc : frozen) {
            uc.setStatus("USED"); uc.setUsedAt(LocalDateTime.now());
            userCouponMapper.updateById(uc);
        }
        log.info("券核销: orderId={}, count={}", orderId, frozen.size());
    }

    @Override @Transactional
    public void release(Long orderId) {
        List<UserCoupon> frozen = userCouponMapper.selectList(new LambdaQueryWrapper<UserCoupon>()
                .eq(UserCoupon::getSourceOrderId, orderId).eq(UserCoupon::getStatus, "FROZEN"));
        for (UserCoupon uc : frozen) {
            uc.setStatus("UNUSED"); uc.setSourceOrderId(null); uc.setLockedAt(null);
            userCouponMapper.updateById(uc);
        }
        log.info("券释放: orderId={}, count={}", orderId, frozen.size());
    }

    public CalculateResponse calculate(CalculateRequest req) {
        long totalAmount = 0;
        if (req.getItems() != null)
            totalAmount = req.getItems().stream().mapToLong(i -> i.getPrice() * i.getCount()).sum();

        Map<Long, Long> ruleResults = ruleEngine.execute(totalAmount,
                req.getItems() != null ? req.getItems() : Collections.emptyList());
        long discountAmount = ruleResults.values().stream().mapToLong(Long::longValue).sum();

        List<CalculateResponse.Detail> details = new ArrayList<>();
        for (Map.Entry<Long, Long> e : ruleResults.entrySet()) {
            PromotionRule r = ruleMapper.selectById(e.getKey());
            if (r != null) details.add(CalculateResponse.Detail.builder()
                    .ruleName(r.getName()).discountAmount(e.getValue()).build());
        }

        List<CalculateResponse.AvailableCoupon> coupons = new ArrayList<>();
        if (req.getUserId() != null) {
            for (UserCoupon uc : mine(req.getUserId(), "UNUSED")) {
                CouponTemplate t = templateMapper.selectById(uc.getCouponTemplateId());
                if (t != null && totalAmount >= t.getThresholdAmount()) {
                    long save = computeSave(t, totalAmount);
                    coupons.add(CalculateResponse.AvailableCoupon.builder()
                            .couponId(uc.getId()).saveAmount(save).build());
                }
            }
        }

        long finalAmount = Math.max(0, totalAmount - discountAmount
                - (req.getCouponId() != null ? computeCouponDiscount(req.getCouponId(), totalAmount) : 0));

        DiscountSnapshot snap = new DiscountSnapshot();
        snap.setOrderNo("CALC_" + UUID.randomUUID().toString().substring(0, 8));
        snap.setTotalAmount(totalAmount); snap.setDiscountAmount(discountAmount);
        snap.setFinalAmount(finalAmount);
        snapshotMapper.insert(snap);

        return CalculateResponse.builder()
                .totalAmount(totalAmount).discountAmount(discountAmount).finalAmount(finalAmount)
                .detail(details).availableCoupons(coupons).build();
    }

    private long computeSave(CouponTemplate t, long total) { return computeDiscount(t, total); }
    private long computeCouponDiscount(Long userCouponId, long total) {
        UserCoupon uc = userCouponMapper.selectById(userCouponId);
        if (uc == null) return 0;
        return computeDiscount(templateMapper.selectById(uc.getCouponTemplateId()), total);
    }
    private long computeDiscount(CouponTemplate t, long total) {
        if (t == null || total < t.getThresholdAmount()) return 0;
        return switch (t.getType()) {
            case "FULL_REDUCE", "CASH" -> Math.min(t.getDiscountValue(), total);
            case "DISCOUNT" -> total - (total * t.getDiscountValue() / 100);
            default -> 0L;
        };
    }

    /** 营销方案建议（演示口径：基于 SKU 热度/库存/趋势的促销策略与触达渠道；正式版聚合销量/库存/券/拼团数据） */
    public List<Map<String, Object>> marketingPlan() {
        List<Map<String, Object>> out = new ArrayList<>();
        Map<String, Object> p1 = new LinkedHashMap<>();
        p1.put("skuId", "9000000000000000022");
        p1.put("productName", "无线蓝牙耳机 Pro");
        p1.put("strategy", "会员价");
        p1.put("detail", "会员价 ¥189（9.5 折），新客下单再送 10 元券");
        p1.put("reason", "热销且增长（+50%），用会员价沉淀复购，不伤价格锚点");
        p1.put("channels", List.of("App 首页 Banner", "会员中心", "Push 推送"));
        out.add(p1);

        Map<String, Object> p2 = new LinkedHashMap<>();
        p2.put("skuId", "9000000000000000011");
        p2.put("productName", "智能手环 5");
        p2.put("strategy", "拼团引流");
        p2.put("detail", "2 人团 ¥149（-6%），成团返 5 元无门槛券");
        p2.put("reason", "需求上行但库存偏低，拼团引流的同时以小单量周转补货周期");
        p2.put("channels", List.of("拼团页", "短信触达老客", "社群分享"));
        out.add(p2);

        Map<String, Object> p3 = new LinkedHashMap<>();
        p3.put("skuId", "9000000000000000033");
        p3.put("productName", "便携榨汁杯");
        p3.put("strategy", "满减清库存");
        p3.put("detail", "满 2 件 8 折 / 买 1 赠 1 限量，售罄即止");
        p3.put("reason", "需求走弱（-50%），降价清库存回笼资金，避免长期占仓");
        p3.put("channels", List.of("首页特卖区", "短信触达历史购买用户", "直播间限时"));
        out.add(p3);
        return out;
    }
}