package com.zhigou.product.service.impl;

import com.zhigou.product.dto.CompareOffer;
import com.zhigou.product.dto.PriceCompareResponse;
import com.zhigou.product.entity.ProductSku;
import com.zhigou.product.mapper.ProductSkuMapper;
import com.zhigou.product.price.PriceSourceAdapter;
import com.zhigou.product.service.PriceCompareService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * 跨平台比价实现（P1 六批）。
 *
 * <p>聚合所有 {@link PriceSourceAdapter} 报价，按总价（售价+运费）最低标记最优渠道，
 * 输出一句话最优建议（含到货天数权衡）。渠道全失败时降级为仅本平台价。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PriceCompareServiceImpl implements PriceCompareService {

    private final ProductSkuMapper skuMapper;
    private final List<PriceSourceAdapter> adapters;

    @Override
    public List<PriceCompareResponse> compare(List<Long> skuIds) {
        List<PriceCompareResponse> result = new ArrayList<>();
        if (skuIds == null || skuIds.isEmpty()) return result;

        for (Long skuId : skuIds.stream().distinct().limit(10).toList()) {
            ProductSku sku = skuMapper.selectOne(
                    new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<ProductSku>()
                            .eq(ProductSku::getSkuId, skuId));
            if (sku == null) {
                log.warn("比价跳过：SKU {} 不存在", skuId);
                continue;
            }

            List<CompareOffer> offers = new ArrayList<>();
            for (PriceSourceAdapter adapter : adapters) {
                try {
                    CompareOffer offer = adapter.fetchOffer(skuId);
                    if (offer != null) offers.add(offer);
                } catch (Exception e) {
                    log.warn("渠道 {} 报价失败 skuId={}: {}", adapter.sourceName(), skuId, e.getMessage());
                }
            }

            // 无任何渠道报价时，以本平台价为兜底渠道（标注来源）
            if (offers.isEmpty()) {
                offers.add(CompareOffer.builder()
                        .source("智购自营")
                        .price(sku.getPrice())
                        .shippingFee(0L)
                        .totalPrice(sku.getPrice())
                        .deliveryDays(2)
                        .promoText("官方自营 · 全场包邮")
                        .build());
            }

            // 最优 = 总价最低；同价取到货更快者
            CompareOffer best = offers.stream()
                    .min(Comparator.comparing(CompareOffer::getTotalPrice)
                            .thenComparing(CompareOffer::getDeliveryDays))
                    .orElse(offers.get(0));
            offers.forEach(o -> o.setIsBest(o == best));

            String skuName = (sku.getSpecValue() != null && !sku.getSpecValue().isBlank())
                    ? sku.getSpecValue() : (sku.getSpecName() != null ? sku.getSpecName() : "SKU-" + skuId);
            String suggestion = String.format("「%s」最优：%s 总价 ¥%.2f（含运费），约 %d 天到货",
                    skuName, best.getSource(), best.getTotalPrice() / 100.0, best.getDeliveryDays());

            result.add(PriceCompareResponse.builder()
                    .skuId(sku.getSkuId())
                    .spuId(sku.getSpuId())
                    .skuName(skuName)
                    .offers(offers)
                    .bestSource(best.getSource())
                    .bestTotalPrice(best.getTotalPrice())
                    .suggestion(suggestion)
                    .build());
        }
        return result;
    }
}
