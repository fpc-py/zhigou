package com.zhigou.marketing.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("group_buy_order")
public class GroupBuyOrder {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long activityId;
    private Long leaderUserId;
    private Integer targetSize;
    private String status;
    private LocalDateTime expireTime;
    private LocalDateTime successTime;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
    @Version
    private Integer version;
    @TableLogic
    private Integer deleted;
}
