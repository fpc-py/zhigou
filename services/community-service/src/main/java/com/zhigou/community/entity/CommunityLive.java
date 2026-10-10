package com.zhigou.community.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@TableName("community_live")
public class CommunityLive {
    @TableId(type = IdType.ASSIGN_ID)
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;
    @JsonSerialize(using = ToStringSerializer.class)
    private Long authorId;
    private String authorName;
    @JsonSerialize(using = ToStringSerializer.class)
    private Long spuId;
    private String title;
    private String coverUrl;
    private Integer status;
    private Integer viewCount;
    private Integer likeCount;
    private LocalDateTime scheduledStart;
    private LocalDateTime startedAt;
    private LocalDateTime endedAt;
    private Integer deleted;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
