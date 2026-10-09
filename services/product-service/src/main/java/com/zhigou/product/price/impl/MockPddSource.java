package com.zhigou.product.price.impl;

import com.zhigou.product.dto.CompareOffer;
import com.zhigou.product.entity.ProductSku;
import com.zhigou.product.mapper.ProductSkuMapper;
import com.zhigou.product.price.PriceSourceAdapter;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 拼多多渠道报价（本地模拟源）。
 *
 * <p><b>演示数据源：</b>生产环境替换为拼多多开放平台比价 API 实现。
 */
@Component
@RequiredArgsConstructor
public class MockPddSource implements PriceSourceAdapter {

    private final ProductSkuMapper skuMapper;

    @Override
    public String sourceName() {
        return "拼多多";
    }

    @Override
    public CompareOffer fetchOffer(Long skuId) {
        ProductSku sku = skuMapper.selectOne(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<ProductSku>()
                        .eq(ProductSku::getSkuId, skuId));
        if (sku == null || sku.getPrice() == null) return null;
        long base = sku.getPrice();
        long price = Math.round(base * 0.88 / 10.0) * 10;          // 百亿补贴价
        long shipping = 300;                                       // 运费 3 元
        return CompareOffer.builder()
                .source(sourceName())
                .price(price)
                .shippingFee(shipping)
                .totalPrice(price + shipping)
                .deliveryDays(5)
                .promoText("百亿补贴 · 到货较慢")
                .build();
    }
}
