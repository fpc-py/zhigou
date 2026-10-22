package com.zhigou.product.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zhigou.product.dto.PriceAlertVO;
import com.zhigou.product.dto.PriceNoticeVO;
import com.zhigou.product.entity.PriceAlert;
import com.zhigou.product.entity.PriceHistory;
import com.zhigou.product.entity.PriceNotice;
import com.zhigou.product.entity.ProductSku;
import com.zhigou.product.mapper.PriceAlertMapper;
import com.zhigou.product.mapper.PriceHistoryMapper;
import com.zhigou.product.mapper.PriceNoticeMapper;
import com.zhigou.product.mapper.ProductSkuMapper;
import com.zhigou.product.service.PriceAlertService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 降价关注与提醒实现（AI 省钱助手 · B 块）。
 *
 * <p>设计要点（面试叙事）：
 * 1. <b>关注制触达</b>——提醒由数据模型保证零打扰：无 price_alert 记录 = 检测任务扫不到 = 零推送；
 * 2. <b>提醒去重</b>——每次命中后 watch_price 推进为新价，同一价格只提醒一次，继续盯更低；
 * 3. <b>目标价语义</b>——target_price 非空时以「最新价 ≤ target_price」触发；为空则以「最新价 < watch_price」触发。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PriceAlertServiceImpl implements PriceAlertService {

    private final PriceAlertMapper alertMapper;
    private final PriceNoticeMapper noticeMapper;
    private final PriceHistoryMapper historyMapper;
    private final ProductSkuMapper skuMapper;

    @Override
    @Transactional
    public PriceAlertVO watch(Long userId, Long skuId, Long targetPrice) {
        ProductSku sku = skuMapper.selectOne(new LambdaQueryWrapper<ProductSku>()
                .eq(ProductSku::getSkuId, skuId));
        if (sku == null || sku.getPrice() == null) {
            throw new IllegalArgumentException("SKU 不存在或价格缺失: " + skuId);
        }
        Long current = sku.getPrice();

        PriceAlert existing = alertMapper.selectOne(new LambdaQueryWrapper<PriceAlert>()
                .eq(PriceAlert::getUserId, userId)
                .eq(PriceAlert::getSkuId, skuId));
        PriceAlert alert;
        if (existing == null) {
            alert = new PriceAlert();
            alert.setUserId(userId);
            alert.setSkuId(skuId);
            alert.setWatchPrice(current);
            alert.setTargetPrice(targetPrice);
            alert.setStatus("ACTIVE");
            alert.setCreatedAt(LocalDateTime.now());
            alert.setUpdatedAt(LocalDateTime.now());
            alertMapper.insert(alert);
        } else {
            alert = existing;
            alert.setWatchPrice(current); // 重新关注 = 以当前价为新基准
            alert.setTargetPrice(targetPrice);
            alert.setStatus("ACTIVE");
            alert.setUpdatedAt(LocalDateTime.now());
            alertMapper.updateById(alert);
        }
        return toVO(alert, current);
    }

    @Override
    @Transactional
    public void unwatch(Long userId, Long skuId) {
        PriceAlert alert = alertMapper.selectOne(new LambdaQueryWrapper<PriceAlert>()
                .eq(PriceAlert::getUserId, userId)
                .eq(PriceAlert::getSkuId, skuId));
        if (alert != null) {
            alert.setStatus("OFF");
            alert.setUpdatedAt(LocalDateTime.now());
            alertMapper.updateById(alert);
        }
    }

    @Override
    public List<PriceAlertVO> mine(Long userId) {
        List<PriceAlert> alerts = alertMapper.selectList(new LambdaQueryWrapper<PriceAlert>()
                .eq(PriceAlert::getUserId, userId)
                .eq(PriceAlert::getStatus, "ACTIVE")
                .orderByDesc(PriceAlert::getUpdatedAt));
        List<PriceAlertVO> result = new ArrayList<>();
        for (PriceAlert a : alerts) {
            ProductSku sku = skuMapper.selectOne(new LambdaQueryWrapper<ProductSku>()
                    .eq(ProductSku::getSkuId, a.getSkuId()));
            Long current = sku != null ? sku.getPrice() : null;
            result.add(toVO(a, current));
        }
        return result;
    }

    @Override
    public List<PriceNoticeVO> notices(Long userId, boolean unreadOnly) {
        LambdaQueryWrapper<PriceNotice> q = new LambdaQueryWrapper<PriceNotice>()
                .eq(PriceNotice::getUserId, userId)
                .orderByDesc(PriceNotice::getCreatedAt);
        if (unreadOnly) q.eq(PriceNotice::getReadFlag, 0);
        List<PriceNotice> list = noticeMapper.selectList(q);
        List<PriceNoticeVO> result = new ArrayList<>();
        for (PriceNotice n : list) {
            ProductSku sku = skuMapper.selectOne(new LambdaQueryWrapper<ProductSku>()
                    .eq(ProductSku::getSkuId, n.getSkuId()));
            String name = skuName(sku, n.getSkuId());
            result.add(PriceNoticeVO.builder()
                    .id(n.getId())
                    .skuId(n.getSkuId())
                    .skuName(name)
                    .oldPrice(n.getOldPrice())
                    .newPrice(n.getNewPrice())
                    .dropAmount(n.getOldPrice() - n.getNewPrice())
                    .readFlag(n.getReadFlag())
                    .createdAt(n.getCreatedAt())
                    .build());
        }
        return result;
    }

    @Override
    public int markRead(Long userId, Long noticeId) {
        PriceNotice n = noticeMapper.selectById(noticeId);
        if (n == null || !n.getUserId().equals(userId)) return 0;
        n.setReadFlag(1);
        noticeMapper.updateById(n);
        return 1;
    }

    @Override
    @Transactional
    public int checkDrops() {
        List<PriceAlert> active = alertMapper.selectList(new LambdaQueryWrapper<PriceAlert>()
                .eq(PriceAlert::getStatus, "ACTIVE"));
        int hit = 0;
        for (PriceAlert a : active) {
            // 取该 SKU 最新一条快照价
            List<PriceHistory> last = historyMapper.selectList(new LambdaQueryWrapper<PriceHistory>()
                    .eq(PriceHistory::getSkuId, a.getSkuId())
                    .orderByDesc(PriceHistory::getSnapshotAt)
                    .last("LIMIT 1"));
            if (last.isEmpty()) continue;
            Long latest = last.get(0).getPrice();
            boolean triggered;
            if (a.getTargetPrice() != null) {
                triggered = latest <= a.getTargetPrice();
            } else {
                triggered = latest < a.getWatchPrice();
            }
            if (triggered) {
                PriceNotice notice = new PriceNotice();
                notice.setUserId(a.getUserId());
                notice.setSkuId(a.getSkuId());
                notice.setOldPrice(a.getWatchPrice());
                notice.setNewPrice(latest);
                notice.setReadFlag(0);
                notice.setCreatedAt(LocalDateTime.now());
                noticeMapper.insert(notice);
                // 推进基准价：同一价格只提醒一次，继续盯更低
                a.setWatchPrice(latest);
                a.setUpdatedAt(LocalDateTime.now());
                alertMapper.updateById(a);
                hit++;
                log.info("降价提醒命中：user={} sku={} {}→{}", a.getUserId(), a.getSkuId(), a.getWatchPrice(), latest);
            }
        }
        log.info("降价检测完成：扫描 {} 条关注，命中 {} 条", active.size(), hit);
        return hit;
    }

    private PriceAlertVO toVO(PriceAlert a, Long current) {
        boolean dropped = current != null && current < a.getWatchPrice();
        return PriceAlertVO.builder()
                .id(a.getId())
                .skuId(a.getSkuId())
                .skuName(skuName(skuMapper.selectOne(new LambdaQueryWrapper<ProductSku>()
                        .eq(ProductSku::getSkuId, a.getSkuId())), a.getSkuId()))
                .watchPrice(a.getWatchPrice())
                .currentPrice(current)
                .targetPrice(a.getTargetPrice())
                .dropped(dropped)
                .dropAmount(dropped ? a.getWatchPrice() - current : 0L)
                .status(a.getStatus())
                .updatedAt(a.getUpdatedAt())
                .build();
    }

    private String skuName(ProductSku sku, Long skuId) {
        if (sku == null) return "SKU-" + skuId;
        if (sku.getSpecValue() != null && !sku.getSpecValue().isBlank()) return sku.getSpecValue();
        if (sku.getSpecName() != null) return sku.getSpecName();
        return "SKU-" + skuId;
    }
}
