package com.zhigou.marketing.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("group_buy_activity")
public class GroupBuyActivity {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long skuId;
    private Long spuId;
    private String title;
    private String imageUrl;
    private Long soloPrice;
    private Long groupPrice;
    private Integer groupSize;
    private Integer groupStock;
    private Integer limitMinutes;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private Integer status;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
    @Version
    private Integer version;
    @TableLogic
    private Integer deleted;
}
