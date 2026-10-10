package com.zhigou.closet.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Data;

import java.time.LocalDateTime;

/** 衣橱衣物单品 */
@Data
@TableName("closet_item")
public class ClosetItem {
    @TableId(type = IdType.ASSIGN_ID)
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;
    @JsonSerialize(using = ToStringSerializer.class)
    private Long userId;
    private String name;
    /** 上装/下装/外套/鞋履/配饰 */
    private String category;
    /** 春夏秋冬/四季 */
    private String season;
    private String color;
    private String imageUrl;
    private String tags;
    private Integer wearCount;
    private LocalDateTime lastWornAt;
    private Integer status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
