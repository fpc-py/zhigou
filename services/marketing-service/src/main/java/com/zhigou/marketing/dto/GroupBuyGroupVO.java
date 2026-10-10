package com.zhigou.marketing.dto;

import lombok.Builder;
import lombok.Data;
import java.time.LocalDateTime;

/** 团单进度视图 */
@Data @Builder
public class GroupBuyGroupVO {
    private Long groupId;
    private Long leaderUserId;
    private Integer memberCount;
    private Integer targetSize;
    private Integer remain;
    private String status;
    private LocalDateTime expireTime;
}
