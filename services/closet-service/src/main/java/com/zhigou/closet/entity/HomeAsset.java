package com.zhigou.closet.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

/** 家居盘点 */
@Data
@TableName("home_asset")
public class HomeAsset {
    @TableId(type = IdType.ASSIGN_ID)
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;
    @JsonSerialize(using = ToStringSerializer.class)
    private Long userId;
    private String name;
    /** 食品/日用品/家电/清洁 */
    private String category;
    private Integer quantity;
    private String unit;
    private LocalDate expireAt;
    private Integer replenishAlert;
    private LocalDateTime updatedAt;
    private LocalDateTime createdAt;
}
