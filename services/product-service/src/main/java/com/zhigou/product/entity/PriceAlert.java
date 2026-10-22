package com.zhigou.product.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Data;

import java.time.LocalDateTime;

/** 用户降价关注（AI 省钱助手 · B 块，关注制不骚扰） */
@Data
@TableName("price_alert")
public class PriceAlert {

    @TableId(type = IdType.AUTO)
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    @JsonSerialize(using = ToStringSerializer.class)
    private Long userId;

    @JsonSerialize(using = ToStringSerializer.class)
    private Long skuId;

    /** 期望价（分），NULL=任意降价都提醒 */
    private Long targetPrice;

    /** 关注时价格（分），检测基准价 */
    private Long watchPrice;

    /** ACTIVE / PAUSED / OFF */
    private String status;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
