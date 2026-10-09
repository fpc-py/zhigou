package com.zhigou.product.price;

import com.zhigou.product.dto.CompareOffer;

/**
 * 价格数据源适配器（P1 六批 · 跨平台比价）。
 *
 * <p>每个实现代表一个比价渠道（京东 / 天猫 / 拼多多…）。
 * 生产环境将本地模拟实现替换为真实第三方比价 API 实现（实现同一接口即可，
 * 参见 {@code docs/生产接入文档}），业务聚合层不感知来源差异。
 */
public interface PriceSourceAdapter {

    /** 平台/渠道名（展示用），如 "京东" */
    String sourceName();

    /** 获取某 SKU 在该渠道的报价；无法获取时返回 null */
    CompareOffer fetchOffer(Long skuId);
}
