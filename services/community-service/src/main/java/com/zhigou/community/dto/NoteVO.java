package com.zhigou.community.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/** 笔记视图（列表/详情） */
@Data
public class NoteVO {
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;
    @JsonSerialize(using = ToStringSerializer.class)
    private Long authorId;
    private String authorName;
    @JsonSerialize(using = ToStringSerializer.class)
    private Long spuId;
    private String spuName;
    private String title;
    private String content;
    private List<String> images;
    private Integer likeCount;
    private Integer favoriteCount;
    private Integer commentCount;
    private Integer fakeFlag;
    private Boolean liked;
    private Boolean favorited;
    private LocalDateTime createdAt;
    private List<Map<String, Object>> comments; // 详情时携带
}
