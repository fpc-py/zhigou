package com.zhigou.product.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zhigou.product.dto.PriceHistoryVO;
import com.zhigou.product.entity.PriceHistory;
import com.zhigou.product.entity.ProductSku;
import com.zhigou.product.mapper.PriceHistoryMapper;
import com.zhigou.product.mapper.ProductSkuMapper;
import com.zhigou.product.service.PriceHistoryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 历史价格实现（AI 省钱助手 · A 块）。
 *
 * <p>采集策略：每日全量快照（@Scheduled 触发）+ 查询时单 SKU 补点（当天无快照写当前价）。
 * 口径：按天去重取当天最后一条；最低价 = 近 N 天快照最小值；diffFromLowest = 现价 - 最低价。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PriceHistoryServiceImpl implements PriceHistoryService {

    private final PriceHistoryMapper historyMapper;
    private final ProductSkuMapper skuMapper;

    @Override
    public int snapshotAll() {
        List<ProductSku> skus = skuMapper.selectList(null);
        int written = 0;
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime todayStart = LocalDateTime.of(LocalDate.now(), LocalTime.MIN);
        for (ProductSku sku : skus) {
            Long skuId = sku.getSkuId();
            if (skuId == null || sku.getPrice() == null) continue;
            // 当天已有快照则跳过（幂等）
            Long cnt = historyMapper.selectCount(new LambdaQueryWrapper<PriceHistory>()
                    .eq(PriceHistory::getSkuId, skuId)
                    .ge(PriceHistory::getSnapshotAt, todayStart));
            if (cnt != null && cnt > 0) continue;
            PriceHistory h = new PriceHistory();
            h.setSkuId(skuId);
            h.setPrice(sku.getPrice());
            h.setSnapshotAt(now);
            historyMapper.insert(h);
            written++;
        }
        log.info("历史价格每日快照完成：扫描 {} SKU，写入 {} 条", skus.size(), written);
        return written;
    }

    @Override
    public void ensureSnapshot(Long skuId) {
        if (skuId == null) return;
        LocalDateTime todayStart = LocalDateTime.of(LocalDate.now(), LocalTime.MIN);
        Long cnt = historyMapper.selectCount(new LambdaQueryWrapper<PriceHistory>()
                .eq(PriceHistory::getSkuId, skuId)
                .ge(PriceHistory::getSnapshotAt, todayStart));
        if (cnt != null && cnt > 0) return;
        ProductSku sku = skuMapper.selectOne(new LambdaQueryWrapper<ProductSku>()
                .eq(ProductSku::getSkuId, skuId));
        if (sku == null || sku.getPrice() == null) return;
        PriceHistory h = new PriceHistory();
        h.setSkuId(skuId);
        h.setPrice(sku.getPrice());
        h.setSnapshotAt(LocalDateTime.now());
        historyMapper.insert(h);
    }

    @Override
    public PriceHistoryVO history(Long skuId, int days) {
        ensureSnapshot(skuId);
        LocalDateTime since = LocalDateTime.of(LocalDate.now().minusDays(Math.max(days, 1) - 1L), LocalTime.MIN);
        List<PriceHistory> rows = historyMapper.selectList(new LambdaQueryWrapper<PriceHistory>()
                .eq(PriceHistory::getSkuId, skuId)
                .ge(PriceHistory::getSnapshotAt, since)
                .orderByDesc(PriceHistory::getSnapshotAt));

        ProductSku sku = skuMapper.selectOne(new LambdaQueryWrapper<ProductSku>()
                .eq(ProductSku::getSkuId, skuId));
        Long current = sku != null ? sku.getPrice() : null;

        // 按天去重（取当天最后一条）——rows 已按时间倒序，用 putIfAbsent 取当天最早遇到的=最新
        Map<LocalDate, Long> daily = new LinkedHashMap<>();
        for (PriceHistory r : rows) {
            daily.putIfAbsent(r.getSnapshotAt().toLocalDate(), r.getPrice());
        }

        Long lowest = rows.stream().map(PriceHistory::getPrice).min(Long::compareTo).orElse(null);
        LocalDate lowestDate = null;
        if (lowest != null) {
            lowestDate = rows.stream()
                    .filter(r -> r.getPrice().equals(lowest))
                    .map(r -> r.getSnapshotAt().toLocalDate())
                    .min(LocalDate::compareTo).orElse(null);
        }
        Long diff = (current != null && lowest != null) ? current - lowest : null;

        String summary;
        if (current == null || lowest == null) {
            summary = "该商品暂无价格历史数据。";
        } else if (diff == 0) {
            summary = String.format("当前价 ¥%.2f 就是近 %d 天最低价，可以放心买。", current / 100.0, days);
        } else {
            summary = String.format("当前价 ¥%.2f 比近 %d 天最低价 ¥%.2f 贵 ¥%.2f，建议关注降价提醒。",
                    current / 100.0, days, lowest / 100.0, diff / 100.0);
        }

        String skuName = (sku != null && sku.getSpecValue() != null && !sku.getSpecValue().isBlank())
                ? sku.getSpecValue() : (sku != null && sku.getSpecName() != null ? sku.getSpecName() : "SKU-" + skuId);

        return PriceHistoryVO.builder()
                .skuId(skuId)
                .skuName(skuName)
                .currentPrice(current)
                .lowestPrice(lowest)
                .lowestDate(lowestDate)
                .diffFromLowest(diff)
                .dailyPrices(daily.entrySet().stream()
                        .sorted(Map.Entry.comparingByKey())
                        .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue, (a, b) -> a, LinkedHashMap::new)))
                .sampledDays(daily.size())
                .summary(summary)
                .build();
    }
}
