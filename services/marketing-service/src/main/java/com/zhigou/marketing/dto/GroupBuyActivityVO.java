package com.zhigou.marketing.dto;

import lombok.Builder;
import lombok.Data;
import java.time.LocalDateTime;
import java.util.List;

/** 拼团活动聚合视图（活动 + 可加入的 OPEN 团单） */
@Data @Builder
public class GroupBuyActivityVO {
    private Long id;
    private Long skuId;
    private Long spuId;
    private String title;
    private String imageUrl;
    private Long soloPrice;
    private Long groupPrice;
    private Integer groupSize;
    private Integer limitMinutes;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private List<GroupBuyGroupVO> openGroups;
}
