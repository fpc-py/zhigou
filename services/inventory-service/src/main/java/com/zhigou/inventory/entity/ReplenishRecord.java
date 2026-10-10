package com.zhigou.inventory.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data @TableName("replenish_record")
public class ReplenishRecord {
    @TableId(type = IdType.AUTO) private Long id;
    private Long skuId;
    private Integer beforeQty;
    private Integer addQty;
    private Integer afterQty;
    private String triggerType;
    private String remark;
    @TableField(fill = FieldFill.INSERT) private LocalDateTime createTime;
}
