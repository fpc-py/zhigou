package com.zhigou.inventory.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Data;
import java.time.LocalDateTime;

@Data @TableName("replenish_record")
public class ReplenishRecord {
    @JsonSerialize(using = ToStringSerializer.class)
    @TableId(type = IdType.AUTO) private Long id;
    @JsonSerialize(using = ToStringSerializer.class)
    private Long skuId;
    private Integer beforeQty;
    private Integer addQty;
    private Integer afterQty;
    private String triggerType;
    private String remark;
    @TableField(fill = FieldFill.INSERT) private LocalDateTime createTime;
}
