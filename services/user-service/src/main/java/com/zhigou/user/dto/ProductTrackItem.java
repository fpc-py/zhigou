package com.zhigou.user.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 收藏 / 浏览历史 条目（列表展示）
 */
@Data
@Builder
public class ProductTrackItem {

    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    /** 商品 SPU ID（Snowflake，转字符串防 JS 精度丢失） */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long spuId;

    @JsonSerialize(using = ToStringSerializer.class)
    private Long skuId;

    private String spuName;

    /** 价格快照（分） */
    private Long price;

    private String imageUrl;

    /** 浏览次数（浏览历史专用；收藏恒为 1） */
    private Integer browseCount;

    /** 收藏时间 / 最近浏览时间 */
    private LocalDateTime createTime;

    private LocalDateTime lastBrowseTime;
}
