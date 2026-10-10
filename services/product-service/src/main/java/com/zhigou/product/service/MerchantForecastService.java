package com.zhigou.product.service;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zhigou.product.entity.DailySales;
import com.zhigou.product.mapper.DailySalesMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.*;

/**
 * 商家端 AI 经营大脑：销量预测 + 智能选品（演示口径）。
 * 算法为轻量启发式：近 7 天日均 × 天数 × (1+环比趋势)，建议备货 = 预测×1.3 − 当前库存；
 * 正式版应接入时序模型（如 Prophet/ARIMA）与促销日历校准。
 */
@Slf4j @Service @RequiredArgsConstructor
public class MerchantForecastService {
    private final DailySalesMapper dailySalesMapper;

    /** 7/30 天销量预测 + 建议备货（按 SKU 聚合近 14 天日销量） */
    public List<Map<String, Object>> forecast(int days) {
        if (days != 30) days = 7;
        List<DailySales> all = dailySalesMapper.selectList(new LambdaQueryWrapper<DailySales>()
                .orderByAsc(DailySales::getSkuId).orderByAsc(DailySales::getSalesDate));
        Map<String, List<DailySales>> bySku = new java.util.LinkedHashMap<>();
        for (DailySales d : all) bySku.computeIfAbsent(d.getSkuId(), k -> new ArrayList<>()).add(d);

        List<Map<String, Object>> out = new ArrayList<>();
        for (Map.Entry<String, List<DailySales>> e : bySku.entrySet()) {
            List<DailySales> seq = e.getValue();
            if (seq.size() < 7) continue;
            int n = seq.size();
            double last7 = sum(seq, Math.max(0, n - 7), n);
            double prev7 = sum(seq, Math.max(0, n - 14), Math.max(0, n - 7));
            double avg7 = last7 / 7.0;
            double trend = prev7 > 0 ? (last7 - prev7) / prev7 : 0;
            trend = Math.max(-0.5, Math.min(0.5, trend));
            double forecastQty = avg7 * days * (1 + trend);
            int stock = stockOf(e.getKey());
            int suggestStock = (int) Math.max(0, Math.ceil(forecastQty * 1.3 - stock));
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("skuId", e.getKey());
            m.put("productName", seq.get(seq.size() - 1).getProductName());
            m.put("currentStock", stock);
            m.put("last7Total", (int) last7);
            m.put("avgDaily", Math.round(avg7 * 10) / 10.0);
            m.put("trendPct", Math.round(trend * 1000) / 10.0);
            m.put("forecastDays", days);
            m.put("forecastQty", (int) Math.round(forecastQty));
            m.put("suggestStock", suggestStock);
            m.put("hotLevel", last7 >= 90 ? "热销" : (last7 >= 40 ? "平稳" : "走弱"));
            out.add(m);
        }
        out.sort((a, b) -> Double.compare(((Number) b.get("forecastQty")).doubleValue(), ((Number) a.get("forecastQty")).doubleValue()));
        log.info("销量预测: days={}, {} SKU", days, out.size());
        return out;
    }

    /** 智能选品：近 14 天销量热度 + 低库存 + 增长趋势 → 推荐上架/补货（演示口径） */
    public List<Map<String, Object>> smartSelection() {
        List<Map<String, Object>> fc = forecast(7);
        List<Map<String, Object>> out = new ArrayList<>();
        for (Map<String, Object> f : fc) {
            int stock = (Integer) f.get("currentStock");
            boolean lowStock = stock < 10;
            double trend = ((Number) f.get("trendPct")).doubleValue();
            String reason;
            if (lowStock && trend > 5) reason = "低库存 + 销量上行，建议优先补货/加大采购";
            else if (trend > 5) reason = "增长趋势明显，建议增库存承接需求";
            else if (lowStock) reason = "库存偏低，建议补货防断货";
            else if (trend < -10) reason = "销量走弱，建议检查价格/活动策略或减少备货";
            else reason = "销售平稳，维持现有备货即可";
            Map<String, Object> m = new LinkedHashMap<>(f);
            m.put("reason", reason);
            out.add(m);
        }
        return out;
    }

    /** 动态定价建议：趋势/库存/竞品均价 → 调价动作（演示口径，正式版接入价格弹性与促销日历） */
    public List<Map<String, Object>> pricing() {
        List<Map<String, Object>> fc = forecast(7);
        // 演示常量：现价(分)/竞品均价(分)/成本(分)；正式版从商品 SKU 表与 PriceCompare 竞品源读取
        Map<String, int[]> demo = new HashMap<>();
        demo.put("9000000000000000022", new int[]{19900, 18500, 12000});
        demo.put("9000000000000000011", new int[]{15900, 16500, 9500});
        demo.put("9000000000000000033", new int[]{9900, 7900, 5900});

        List<Map<String, Object>> out = new ArrayList<>();
        for (Map<String, Object> f : fc) {
            String skuId = (String) f.get("skuId");
            int[] cfg = demo.getOrDefault(skuId, new int[]{10000, 10000, 6000});
            int price = cfg[0], compAvg = cfg[1], cost = cfg[2];
            int stock = (Integer) f.get("currentStock");
            double trend = ((Number) f.get("trendPct")).doubleValue();
            String level = stock < 10 ? "LOW" : (stock <= 20 ? "MID" : "HIGH");

            String action; int suggest; String reason;
            if (trend > 5 && "LOW".equals(level)) {
                suggest = Math.min(price + (int) Math.round(price * 0.03), (int) Math.round(compAvg * 1.08));
                suggest = Math.round(suggest / 100f) * 100;
                if (Math.abs(suggest - price) * 100.0 / price < 2) {
                    action = "HOLD"; suggest = price;
                    reason = "现价已接近竞品上限，保持现价（趋势上行 + 低库存，优先补货）";
                } else {
                    action = "UP"; reason = "需求上行（+" + trend + "%）且库存偏低，建议提价至 " + (suggest / 100f) + " 元（≤竞品均价 108%），同步补货";
                }
            } else if (trend < -10) {
                suggest = Math.max(price - (int) Math.round(price * 0.07), (int) Math.round(cost * 1.2));
                suggest = Math.round(suggest / 100f) * 100;
                if (Math.abs(suggest - price) * 100.0 / price < 2) {
                    action = "HOLD"; suggest = price;
                    reason = "已接近成本线，保持现价（需求走弱，建议转营销清库存而非继续降价）";
                } else {
                    action = "DOWN"; reason = "需求走弱（" + trend + "%），建议降价至 " + (suggest / 100f) + " 元清库存（不低于成本 120%）";
                }
            } else {
                action = "HOLD"; suggest = price;
                reason = "供需平稳，保持现价（趋势 " + trend + "%，库存 " + level + "）";
            }
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("skuId", skuId);
            m.put("productName", f.get("productName"));
            m.put("currentPriceFen", price);
            m.put("competitorAvgFen", compAvg);
            m.put("inventoryLevel", level);
            m.put("trendPct", f.get("trendPct"));
            m.put("suggestPriceFen", suggest);
            m.put("action", action);
            m.put("reason", reason);
            out.add(m);
        }
        return out;
    }

    private double sum(List<DailySales> seq, int from, int to) {
        double s = 0;
        for (int i = from; i < to && i < seq.size(); i++) s += seq.get(i).getSalesQty() == null ? 0 : seq.get(i).getSalesQty();
        return s;
    }

    private int stockOf(String skuId) {
        // 演示库存：正式版从库存服务/商品 SKU 表实时读取
        if (skuId.endsWith("22")) return 12;
        if (skuId.endsWith("11")) return 8;
        return 5;
    }
}
