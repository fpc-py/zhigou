package com.zhigou.product.price.impl;

import com.zhigou.product.dto.CompareOffer;
import com.zhigou.product.entity.ProductSku;
import com.zhigou.product.mapper.ProductSkuMapper;
import com.zhigou.product.price.PriceSourceAdapter;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 天猫渠道报价（本地模拟源）。
 *
 * <p><b>演示数据源：</b>生产环境替换为淘宝开放平台 / 天猫比价 API 实现。
 */
@Component
@RequiredArgsConstructor
public class MockTmallSource implements PriceSourceAdapter {

    private final ProductSkuMapper skuMapper;

    @Override
    public String sourceName() {
        return "天猫";
    }

    @Override
    public CompareOffer fetchOffer(Long skuId) {
        ProductSku sku = skuMapper.selectOne(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<ProductSku>()
                        .eq(ProductSku::getSkuId, skuId));
        if (sku == null || sku.getPrice() == null) return null;
        long base = sku.getPrice();
        long price = Math.round(base * 0.98 / 10.0) * 10;          // 官方旗舰店略优惠
        long shipping = price >= 9900 ? 0 : 800;                    // 满 99 包邮
        return CompareOffer.builder()
                .source(sourceName())
                .price(price)
                .shippingFee(shipping)
                .totalPrice(price + shipping)
                .deliveryDays(3)
                .promoText(price >= 9900 ? "官方旗舰 · 满 99 包邮" : "官方旗舰 · 运费 8 元")
                .build();
    }
}
