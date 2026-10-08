package com.zhigou.product.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhigou.product.dto.RecommendResponse;
import com.zhigou.product.dto.ReviewStatsVO;
import com.zhigou.product.entity.ProductSku;
import com.zhigou.product.entity.ProductSpu;
import com.zhigou.product.mapper.ProductSkuMapper;
import com.zhigou.product.mapper.ProductSpuMapper;
import com.zhigou.product.service.ProductReviewService;
import com.zhigou.product.service.RecommendService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.*;

/**
 * 个性化推荐实现（P1 五批）。
 *
 * <p>画像全部来自内部真实数据，不编造：
 * <ul>
 *   <li>历史订单：order-service {@code /order/mine} → 品类偏好（SPU 名包含匹配）+ 价位带偏好（订单单价分桶）+ 已购 SPU</li>
 *   <li>购物车：cart-service {@code /cart/mine} → 当前意向 SPU</li>
 *   <li>评价口碑：本服务 product_review 统计 → 均分/差评/刷评（疑似刷评降权）</li>
 * </ul>
 *
 * <p>评分 = 品类命中(1.5) + 价位匹配(1.0) + 口碑分(0.3~1.5) + 意向加成(0.5)
 * − 刷评降权(0.8) − 已购换新惩罚(0.8) + 反常识加成(0.5，冷门高口碑)。
 * 分数与理由服务端可复现，结果按分降序。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RecommendServiceImpl implements RecommendService {

    private final ProductSpuMapper spuMapper;
    private final ProductSkuMapper skuMapper;
    private final ProductReviewService reviewService;
    private final ObjectMapper objectMapper;

    @Value("${recommend.order-service-url:http://localhost:8085}")
    private String orderServiceUrl;
    @Value("${recommend.cart-service-url:http://localhost:8084}")
    private String cartServiceUrl;

    /** 价格分桶（单位：分）——价位带偏好 */
    private static final long[] PRICE_BUCKETS = {10_000L, 30_000L, 100_000L};

    @Override
    public RecommendResponse recommend(Long userId, String scene, int limit) {
        if (limit <= 0) limit = 6;
        limit = Math.min(limit, 20);

        // 1) 拉取行为数据（失败降级：仅用口碑）
        List<String> orderNames = new ArrayList<>();
        Set<Long> boughtSpuIds = new HashSet<>();
        Map<Integer, Integer> priceBandFreq = new HashMap<>();
        boolean hasOrder = false;
        try {
            List<JsonNode> orders = fetchJsonArray(orderServiceUrl + "/order/mine?userId=" + userId, userId);
            hasOrder = !orders.isEmpty();
            for (JsonNode order : orders) {
                JsonNode items = order.get("items");
                if (items == null || !items.isArray()) continue;
                for (JsonNode it : items) {
                    if (it.get("skuName") != null && it.get("skuName").isTextual()) {
                        orderNames.add(it.get("skuName").asText());
                    }
                    if (it.get("price") != null && it.get("price").isNumber()) {
                        long p = it.get("price").asLong();
                        priceBandFreq.merge(bucketIndex(p), 1, Integer::sum);
                    }
                    if (it.get("skuId") != null && it.get("skuId").isTextual()) {
                        Long spuId = skuIdToSpuId(Long.parseLong(it.get("skuId").asText()));
                        if (spuId != null) boughtSpuIds.add(spuId);
                    }
                }
            }
        } catch (Exception e) {
            log.warn("拉取历史订单失败，降级口碑榜: {}", e.getMessage());
        }

        Set<Long> cartSpuIds = new HashSet<>();
        boolean hasCart = false;
        try {
            List<JsonNode> cart = fetchJsonArray(cartServiceUrl + "/cart/mine", userId);
            hasCart = !cart.isEmpty();
            for (JsonNode it : cart) {
                if (it.get("skuId") != null && it.get("skuId").isTextual()) {
                    Long spuId = skuIdToSpuId(Long.parseLong(it.get("skuId").asText()));
                    if (spuId != null) cartSpuIds.add(spuId);
                }
            }
        } catch (Exception e) {
            log.warn("拉取购物车失败，降级口碑榜: {}", e.getMessage());
        }

        // 2) 候选商品：全部上架 SPU
        List<ProductSpu> spus = spuMapper.selectList(
                new LambdaQueryWrapper<ProductSpu>().eq(ProductSpu::getStatus, 1));

        // 3) 品类偏好：订单 skuName 与 SPU 名包含匹配 → categoryId 频次
        Map<Long, Integer> catFreq = new HashMap<>();
        for (ProductSpu spu : spus) {
            for (String name : orderNames) {
                if (name != null && !name.isBlank()
                        && (spu.getName() != null && (spu.getName().contains(name) || name.contains(spu.getName())))) {
                    catFreq.merge(spu.getCategoryId(), 1, Integer::sum);
                    break;
                }
            }
        }
        // 价位带偏好：出现最多的桶（无订单时不生效）
        int favBand = priceBandFreq.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey).orElse(-1);

        // 4) 逐个评分
        List<ProductSpu> list = new ArrayList<>(spus);
        // 反常识判定基准：候选 SPU 销量均值
        double avgSales = spus.stream()
                .mapToLong(s -> s.getSalesVolume() == null ? 0L : s.getSalesVolume())
                .average().orElse(0);
        List<RecommendResponse.RecommendItem> items = new ArrayList<>();
        for (ProductSpu spu : list) {
            RecommendResponse.RecommendItem item = scoreSpu(spu, catFreq, favBand, boughtSpuIds, cartSpuIds, avgSales);
            if (item != null) items.add(item);
        }
        items.sort((a, b) -> Double.compare(b.getScore(), a.getScore()));
        if (items.size() > limit) items = items.subList(0, limit);

        // 5) 场景文案与来源说明
        String sceneText = switch (scene == null ? "home" : scene) {
            case "cart" -> "帮你补齐购物车 · 按当前意向推荐";
            case "detail" -> "看了又看 · 相关好物推荐";
            default -> hasOrder
                    ? "为你推荐 · 基于你的 " + orderNames.size() + " 件历史购买与口碑"
                    : (hasCart ? "为你推荐 · 基于你的购物车与口碑" : "为你推荐 · 口碑优选");
        };
        String sourceDesc = "数据来源：历史订单 + 购物车 + 真实评价统计（无外部数据）";

        return RecommendResponse.builder()
                .sceneText(sceneText)
                .sourceDesc(sourceDesc)
                .items(items)
                .build();
    }

    // ===== 私有 =====

    /** 单个 SPU 评分与理由 */
    private RecommendResponse.RecommendItem scoreSpu(ProductSpu spu,
                                                     Map<Long, Integer> catFreq,
                                                     int favBand,
                                                     Set<Long> boughtSpuIds,
                                                     Set<Long> cartSpuIds,
                                                     double avgSales) {
        List<String> reasons = new ArrayList<>();
        List<String> tags = new ArrayList<>();
        double score = 0.0;

        // 口碑（真实评价统计；无评价给中性分，不编造好评）
        ReviewStatsVO stats = null;
        try {
            stats = reviewService.stats(spu.getSpuId());
        } catch (Exception e) {
            log.warn("评价统计失败 spuId={}: {}", spu.getSpuId(), e.getMessage());
        }
        if (stats != null && stats.getTotal() > 0) {
            double avg = stats.getAvgRating();
            if (avg >= 4.5) score += 1.5;
            else if (avg >= 4.0) score += 1.2;
            else if (avg >= 3.5) score += 0.8;
            else score += 0.3;
            reasons.add(String.format("口碑 %.1f 分（%d 条评价）", avg, stats.getTotal()));
            if (stats.getDuplicateCount() >= 2) {
                score -= 0.8;
                tags.add("⚠ 疑似刷评");
            }
            if (stats.getLowStarCount() > 0 && stats.getTotal() > 0
                    && (double) stats.getLowStarCount() / stats.getTotal() >= 0.3) {
                tags.add("⚠ 差评比例偏高");
            }
        } else {
            score += 0.6;
        }

        // 品类偏好
        Integer freq = catFreq.get(spu.getCategoryId());
        if (freq != null && freq > 0) {
            score += 1.5;
            reasons.add("你常买这个品类");
        }

        // 价位带匹配（以最低价 SKU 判定）
        if (favBand >= 0 && spu.getPriceMin() != null) {
            if (bucketIndex(spu.getPriceMin()) == favBand) {
                score += 1.0;
                reasons.add("在你常用价位带内");
            }
        }

        // 购物车意向
        if (cartSpuIds.contains(spu.getSpuId())) {
            score += 0.5;
            reasons.add("已在你的购物车");
        }

        // 已购换新惩罚
        if (boughtSpuIds.contains(spu.getSpuId())) {
            score -= 0.8;
            tags.add("已购过");
        }

        // 反常识推荐：冷门高口碑（评价≥5 且均分≥4.5 且销量低于候选均值）
        if (stats != null && stats.getTotal() >= 5
                && stats.getAvgRating() >= 4.5
                && spu.getSalesVolume() != null && spu.getSalesVolume() < avgSales) {
            score += 0.5;
            tags.add("小众高口碑");
            reasons.add("小众但口碑扎实");
        }

        return RecommendResponse.RecommendItem.builder()
                .spuId(spu.getSpuId())
                .name(spu.getName())
                .mainImage(spu.getMainImage())
                .priceMin(spu.getPriceMin())
                .score(Math.round(score * 10) / 10.0)
                .avgRating(stats != null && stats.getTotal() > 0 ? stats.getAvgRating() : null)
                .reviewCount(stats != null ? stats.getTotal().intValue() : 0)
                .reasons(reasons.isEmpty() ? List.of("综合口碑不错") : reasons)
                .tags(tags)
                .build();
    }

    /** skuId → 所属 spuId */
    private Long skuIdToSpuId(Long skuId) {
        ProductSku sku = skuMapper.selectOne(
                new LambdaQueryWrapper<ProductSku>().eq(ProductSku::getSkuId, skuId));
        return sku == null ? null : sku.getSpuId();
    }

    /** 价格分桶索引（分）：<10000→0，<30000→1，<100000→2，其余→3 */
    private int bucketIndex(long price) {
        for (int i = 0; i < PRICE_BUCKETS.length; i++) {
            if (price < PRICE_BUCKETS[i]) return i;
        }
        return PRICE_BUCKETS.length;
    }

    /** 内网 GET 拉取数组（Result.data 为数组；x-user-id 透传身份） */
    private List<JsonNode> fetchJsonArray(String url, Long userId) throws java.io.IOException {
        HttpHeaders headers = new HttpHeaders();
        headers.set("x-user-id", String.valueOf(userId));
        ResponseEntity<String> resp = new RestTemplate().exchange(
                url, HttpMethod.GET, new HttpEntity<>(headers), String.class);
        JsonNode root = objectMapper.readTree(resp.getBody());
        JsonNode data = root.get("data");
        List<JsonNode> out = new ArrayList<>();
        if (data != null && data.isArray()) {
            data.forEach(out::add);
        }
        return out;
    }
}
