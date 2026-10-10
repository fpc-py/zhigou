package com.zhigou.community.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Data;

import java.time.LocalDateTime;

/** 点赞/收藏记录（type: 1=like 2=favorite） */
@Data
@TableName("community_interaction")
public class CommunityInteraction {
    @TableId(type = IdType.ASSIGN_ID)
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;
    @JsonSerialize(using = ToStringSerializer.class)
    private Long noteId;
    @JsonSerialize(using = ToStringSerializer.class)
    private Long userId;
    private Integer type;
    private LocalDateTime createdAt;
}
