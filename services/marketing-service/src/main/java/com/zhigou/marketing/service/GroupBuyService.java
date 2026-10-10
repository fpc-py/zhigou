package com.zhigou.marketing.service;

import com.zhigou.marketing.dto.GroupBuyActivityVO;
import com.zhigou.marketing.entity.GroupBuyOrder;
import java.util.List;
import java.util.Map;

public interface GroupBuyService {
    /** 进行中活动列表（含在拼团 OPEN 团单与成团进度） */
    List<GroupBuyActivityVO> activities();

    /** 开团 */
    GroupBuyOrder open(Long activityId, Long userId);

    /** 参团，达到目标人数自动成团 */
    GroupBuyOrder join(Long groupId, Long userId);

    /** 我的团单（开团 + 参团） */
    List<Map<String, Object>> mine(Long userId);

    /** 团单详情（活动 + 成员 + 进度） */
    Map<String, Object> detail(Long groupId);
}
