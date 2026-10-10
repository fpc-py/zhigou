package com.zhigou.community.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Data;

import java.time.LocalDateTime;

/** 种草笔记 */
@Data
@TableName("community_note")
public class CommunityNote {
    @TableId(type = IdType.ASSIGN_ID)
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;
    @JsonSerialize(using = ToStringSerializer.class)
    private Long authorId;
    private String authorName;
    @JsonSerialize(using = ToStringSerializer.class)
    private Long spuId;
    private String title;
    private String content;
    private String images;       // JSON 数组字符串
    private Integer likeCount;
    private Integer favoriteCount;
    private Integer commentCount;
    private Integer fakeFlag;    // 1=疑似营销/重复内容，0=正常
    private Integer status;      // 0=正常 1=删除(逻辑删由 deleted 承载, status 预留隐藏)
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
