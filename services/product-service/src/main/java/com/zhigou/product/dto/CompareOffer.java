package com.zhigou.product.dto;

import lombok.Builder;
import lombok.Data;

/**
 * 单渠道报价（跨平台比价 · P1 六批）。
 */
@Data
@Builder
public class CompareOffer {
    /** 渠道名（京东/天猫/拼多多…） */
    private String source;
    /** 商品售价（单位：分） */
    private Long price;
    /** 运费（单位：分，0 表示包邮） */
    private Long shippingFee;
    /** 总价 = 售价 + 运费（单位：分） */
    private Long totalPrice;
    /** 预计到货天数 */
    private Integer deliveryDays;
    /** 优惠/活动说明 */
    private String promoText;
    /** 是否最优渠道（总价最低） */
    private Boolean isBest;
}
