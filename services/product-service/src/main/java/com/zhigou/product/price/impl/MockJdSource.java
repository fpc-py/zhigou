package com.zhigou.product.price.impl;

import com.zhigou.product.dto.CompareOffer;
import com.zhigou.product.entity.ProductSku;
import com.zhigou.product.mapper.ProductSkuMapper;
import com.zhigou.product.price.PriceSourceAdapter;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 京东渠道报价（本地模拟源）。
 *
 * <p><b>演示数据源：</b>报价由本地 SKU 价 + 渠道系数生成，仅用于打通全链路。
 * 生产环境替换为京东联盟 / 开普勒真实比价 API（实现 {@link PriceSourceAdapter} 即可）。
 */
@Component
@RequiredArgsConstructor
public class MockJdSource implements PriceSourceAdapter {

    private final ProductSkuMapper skuMapper;

    @Override
    public String sourceName() {
        return "京东";
    }

    @Override
    public CompareOffer fetchOffer(Long skuId) {
        ProductSku sku = skuMapper.selectOne(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<ProductSku>()
                        .eq(ProductSku::getSkuId, skuId));
        if (sku == null || sku.getPrice() == null) return null;
        long base = sku.getPrice();
        long price = Math.round(base * 1.05 / 10.0) * 10;          // 略高于本地价
        long shipping = price >= 9900 ? 0 : 600;                    // 满 99 包邮
        return CompareOffer.builder()
                .source(sourceName())
                .price(price)
                .shippingFee(shipping)
                .totalPrice(price + shipping)
                .deliveryDays(1)
                .promoText(price >= 9900 ? "自营次日达 · 满 99 包邮" : "自营次日达 · 运费 6 元")
                .build();
    }
}
