package com.zhigou.product.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhigou.product.dto.CompareOffer;
import com.zhigou.product.dto.CostSaverOverviewVO;
import com.zhigou.product.dto.PriceCompareResponse;
import com.zhigou.product.dto.PriceNoticeVO;
import com.zhigou.product.entity.PriceAlert;
import com.zhigou.product.entity.PriceNotice;
import com.zhigou.product.entity.ProductSku;
import com.zhigou.product.mapper.PriceAlertMapper;
import com.zhigou.product.mapper.PriceNoticeMapper;
import com.zhigou.product.mapper.ProductSkuMapper;
import com.zhigou.product.service.CostSaverService;
import com.zhigou.product.service.PriceCompareService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 省钱助手总览实现（AI 省钱助手 · C 块）。
 *
 * <p>口径独立合并（面试叙事）：
 * <ul>
 *   <li>满减实省 = marketing /discount/calculate 返回的 discountAmount（购物车确定结算值）；</li>
 *   <li>最优券预估 = UNUSED 券最大面额（单列预估，不计入 total，避免与满减重复）；</li>
 *   <li>比价最优差 = Σ(本平台价×件数) - Σ(跨平台最优总价×件数)，正数=换渠道可省；</li>
 *   <li>降价已省 = 未读 price_notice 降幅合计（已发生的确定值）。</li>
 * </ul>
 * totalSaving 只并入口径独立的三项（满减实省 + 比价最优差 + 降价已省）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CostSaverServiceImpl implements CostSaverService {

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final PriceCompareService priceCompareService;
    private final PriceAlertMapper alertMapper;
    private final PriceNoticeMapper noticeMapper;
    private final ProductSkuMapper skuMapper;

    @Value("${cart-service.url:http://localhost:8084}")
    private String cartServiceUrl;

    @Value("${marketing-service.url:http://localhost:8088}")
    private String marketingServiceUrl;

    @Override
    public CostSaverOverviewVO overview(Long userId) {
        // 1) 购物车选中项
        List<Map<String, Object>> items = cartItems(userId);
        Long cartDiscount = 0L;
        Long couponBest = 0L;
        int usableCoupons = 0;
        long platformTotal = 0L;

        if (items != null && !items.isEmpty()) {
            List<Map<String, Object>> selected = new ArrayList<>();
            for (Map<String, Object> it : items) {
                if (Boolean.TRUE.equals(it.get("selected")) || it.get("selected") == null) {
                    selected.add(it);
                }
            }
            if (!selected.isEmpty()) {
                // 2) 满减实省（marketing /discount/calculate）
                List<Map<String, Object>> calcItems = new ArrayList<>();
                for (Map<String, Object> it : selected) {
                    Long skuId = toLong(it.get("skuId"));
                    int count = toInt(it.get("count"), 1);
                    long price = toLong(it.get("priceAtAdd"), 0L);
                    if (price <= 0) {
                        ProductSku sku = skuMapper.selectOne(new LambdaQueryWrapper<ProductSku>()
                                .eq(ProductSku::getSkuId, skuId));
                        price = sku != null && sku.getPrice() != null ? sku.getPrice() : 0L;
                    }
                    Map<String, Object> ci = new HashMap<>();
                    ci.put("skuId", skuId);
                    ci.put("count", count);
                    ci.put("price", price);
                    calcItems.add(ci);
                    platformTotal += price * count;
                }
                Map<String, Object> calcResp = postJson(marketingServiceUrl + "/discount/calculate",
                        Map.of("userId", userId, "items", calcItems));
                cartDiscount = calcResp != null ? toLong(calcResp.get("discountAmount"), 0L) : 0L;

                // 3) 可用券（UNUSED 最大面额，单列预估）
                JsonNode coupons = getJson(marketingServiceUrl + "/coupon/mine?userId=" + userId + "&status=UNUSED");
                if (coupons != null && coupons.isArray()) {
                    usableCoupons = coupons.size();
                    for (JsonNode c : coupons) {
                        long amount = c.path("amount").asLong(0);
                        if (amount > couponBest) couponBest = amount;
                    }
                }

                // 4) 比价最优差（product 本服务 price/compare）
                List<Long> skuIds = new ArrayList<>();
                for (Map<String, Object> it : selected) skuIds.add(toLong(it.get("skuId")));
                List<PriceCompareResponse> compares = priceCompareService.compare(skuIds);
                long bestTotal = 0L;
                String bestSource = null;
                for (PriceCompareResponse cmp : compares) {
                    // 若最优渠道就是智购自营则无差
                    long perBest = 0L;
                    for (CompareOffer offer : cmp.getOffers()) {
                        if (Boolean.TRUE.equals(offer.getIsBest())) {
                            perBest = offer.getTotalPrice();
                            bestSource = offer.getSource();
                            break;
                        }
                    }
                    if (perBest > 0) bestTotal += perBest;
                }
                if (bestSource != null && bestTotal > 0 && bestTotal < platformTotal) {
                    bestSource = bestSource;
                }
                long compareSaving = Math.max(0L, platformTotal - bestTotal);
                return assemble(userId, cartDiscount, couponBest, usableCoupons,
                        compareSaving, bestSource, items.size());
            }
        }

        // 空购物车：只报降价/关注侧
        return assemble(userId, 0L, couponBest, usableCoupons, 0L, null,
                items == null ? 0 : items.size());
    }

    private CostSaverOverviewVO assemble(Long userId, Long cartDiscount, Long couponBest,
                                         int usableCoupons, long compareSaving, String bestSource,
                                         int cartCount) {
        // 降价已省 = 未读提醒降幅合计
        List<PriceNotice> unread = noticeMapper.selectList(new LambdaQueryWrapper<PriceNotice>()
                .eq(PriceNotice::getUserId, userId)
                .eq(PriceNotice::getReadFlag, 0)
                .orderByDesc(PriceNotice::getCreatedAt)
                .last("LIMIT 10"));
        long noticeSaving = 0L;
        for (PriceNotice n : unread) noticeSaving += n.getOldPrice() - n.getNewPrice();

        List<PriceNoticeVO> recent = new ArrayList<>();
        int show = Math.min(unread.size(), 3);
        for (int i = 0; i < show; i++) {
            PriceNotice n = unread.get(i);
            recent.add(PriceNoticeVO.builder()
                    .id(n.getId()).skuId(n.getSkuId())
                    .skuName(skuName(n.getSkuId()))
                    .oldPrice(n.getOldPrice()).newPrice(n.getNewPrice())
                    .dropAmount(n.getOldPrice() - n.getNewPrice())
                    .readFlag(n.getReadFlag()).createdAt(n.getCreatedAt())
                    .build());
        }

        Long watchCount = alertMapper.selectCount(new LambdaQueryWrapper<PriceAlert>()
                .eq(PriceAlert::getUserId, userId)
                .eq(PriceAlert::getStatus, "ACTIVE"));

        long total = cartDiscount + compareSaving + noticeSaving;
        StringBuilder sb = new StringBuilder("省钱助手：");
        if (total > 0) {
            sb.append("当前帮你省了 ¥").append(total / 100.0);
            List<String> parts = new ArrayList<>();
            if (cartDiscount > 0) parts.add("满减 ¥" + cartDiscount / 100.0);
            if (compareSaving > 0) parts.add("比价 ¥" + compareSaving / 100.0);
            if (noticeSaving > 0) parts.add("降价 ¥" + noticeSaving / 100.0);
            sb.append("（").append(String.join(" + ", parts)).append("）");
        } else {
            sb.append("暂无已兑现的节省。");
        }
        if (couponBest > 0) {
            sb.append("另有 ¥").append(couponBest / 100.0).append(" 可用券待结算抵扣。");
        }
        if (unread.size() > show) sb.append("还有 ").append(unread.size() - show).append(" 条降价提醒待看。");

        return CostSaverOverviewVO.builder()
                .cartDiscountSaving(cartDiscount)
                .couponBestSaving(couponBest)
                .usableCouponCount(usableCoupons)
                .compareSaving(compareSaving)
                .bestSource(bestSource)
                .noticeSaving(noticeSaving)
                .unreadNotices(unread.size())
                .watchCount(watchCount == null ? 0 : watchCount.intValue())
                .cartItemCount(cartCount)
                .totalSaving(total)
                .summary(sb.toString())
                .recentNotices(recent)
                .build();
    }

    // ── HTTP 辅助 ──
    private List<Map<String, Object>> cartItems(Long userId) {
        try {
            JsonNode body = getJson(cartServiceUrl + "/cart/mine", userId);
            if (body == null || !body.isArray()) return List.of();
            List<Map<String, Object>> items = new ArrayList<>();
            for (JsonNode n : body) {
                Map<String, Object> m = new HashMap<>();
                m.put("skuId", n.path("skuId").asLong());
                m.put("count", n.path("count").asInt(1));
                m.put("selected", n.path("selected").asBoolean(true));
                m.put("priceAtAdd", n.path("priceAtAdd").asLong(0));
                items.add(m);
            }
            return items;
        } catch (Exception e) {
            log.warn("cart/mine 拉取失败 userId={}: {}", userId, e.getMessage());
            return null;
        }
    }

    private JsonNode getJson(String url) {
        try {
            ResponseEntity<JsonNode> resp = restTemplate.exchange(url, HttpMethod.GET, null,
                    new ParameterizedTypeReference<JsonNode>() {});
            JsonNode body = resp.getBody();
            return body != null ? body.path("data") : null;
        } catch (Exception e) {
            log.warn("GET {} 失败: {}", url, e.getMessage());
            return null;
        }
    }

    private JsonNode getJson(String url, Long userId) {
        try {
            HttpHeaders headers = new HttpHeaders();
            if (userId != null) headers.set("x-user-id", String.valueOf(userId));
            HttpEntity<Void> entity = new HttpEntity<>(headers);
            ResponseEntity<JsonNode> resp = restTemplate.exchange(url, HttpMethod.GET, entity,
                    new ParameterizedTypeReference<JsonNode>() {});
            JsonNode body = resp.getBody();
            return body != null ? body.path("data") : null;
        } catch (Exception e) {
            log.warn("GET {} 失败: {}", url, e.getMessage());
            return null;
        }
    }

    private Map<String, Object> postJson(String url, Map<String, Object> payload) {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(org.springframework.http.MediaType.APPLICATION_JSON);
            HttpEntity<String> entity = new HttpEntity<>(objectMapper.writeValueAsString(payload), headers);
            ResponseEntity<JsonNode> resp = restTemplate.exchange(url, HttpMethod.POST, entity,
                    new ParameterizedTypeReference<JsonNode>() {});
            JsonNode body = resp.getBody();
            if (body == null) return null;
            JsonNode data = body.path("data");
            return data.isObject() ? objectMapper.convertValue(data, Map.class) : null;
        } catch (Exception e) {
            log.warn("POST {} 失败: {}", url, e.getMessage());
            return null;
        }
    }

    private Long toLong(Object v) { return toLong(v, 0L); }
    private Long toLong(Object v, Long def) {
        if (v == null) return def;
        try { return Long.valueOf(String.valueOf(v)); } catch (Exception e) { return def; }
    }
    private int toInt(Object v, int def) {
        if (v == null) return def;
        try { return Integer.parseInt(String.valueOf(v)); } catch (Exception e) { return def; }
    }

    private String skuName(Long skuId) {
        ProductSku sku = skuMapper.selectOne(new LambdaQueryWrapper<ProductSku>()
                .eq(ProductSku::getSkuId, skuId));
        if (sku == null) return "SKU-" + skuId;
        if (sku.getSpecValue() != null && !sku.getSpecValue().isBlank()) return sku.getSpecValue();
        return sku.getSpecName() != null ? sku.getSpecName() : "SKU-" + skuId;
    }
}
