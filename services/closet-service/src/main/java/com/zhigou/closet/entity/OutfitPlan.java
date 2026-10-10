package com.zhigou.closet.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Data;

import java.time.LocalDateTime;

/** 穿搭方案 */
@Data
@TableName("outfit_plan")
public class OutfitPlan {
    @TableId(type = IdType.ASSIGN_ID)
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;
    @JsonSerialize(using = ToStringSerializer.class)
    private Long userId;
    private String title;
    private String occasion;
    /** JSON 数组：选中的衣物 id 列表 */
    private String itemIds;
    private Integer score;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
