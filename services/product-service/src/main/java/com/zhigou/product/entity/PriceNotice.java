package com.zhigou.product.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Data;

import java.time.LocalDateTime;

/** 降价提醒站内信（AI 省钱助手 · B 块） */
@Data
@TableName("price_notice")
public class PriceNotice {

    @TableId(type = IdType.AUTO)
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    @JsonSerialize(using = ToStringSerializer.class)
    private Long userId;

    @JsonSerialize(using = ToStringSerializer.class)
    private Long skuId;

    /** 降价前基准价（分） */
    private Long oldPrice;

    /** 降价后价格（分） */
    private Long newPrice;

    /** 0 未读 / 1 已读 */
    private Integer readFlag;

    private LocalDateTime createdAt;
}
