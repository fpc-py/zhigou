package com.zhigou.community.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Data;

import java.time.LocalDateTime;

/** 笔记评论 */
@Data
@TableName("community_comment")
public class CommunityComment {
    @TableId(type = IdType.ASSIGN_ID)
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;
    @JsonSerialize(using = ToStringSerializer.class)
    private Long noteId;
    @JsonSerialize(using = ToStringSerializer.class)
    private Long userId;
    private String authorName;
    private String content;
    private LocalDateTime createdAt;
}
