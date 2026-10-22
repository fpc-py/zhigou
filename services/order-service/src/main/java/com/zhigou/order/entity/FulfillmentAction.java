package com.zhigou.order.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Data;
import java.time.LocalDateTime;

@Data @TableName("fulfillment_action")
public class FulfillmentAction {
    @TableId(type = IdType.AUTO) private Long id;
    @JsonSerialize(using = ToStringSerializer.class)
    private Long orderId;
    @JsonSerialize(using = ToStringSerializer.class)
    private Long skuId;
    private String action;
    private String reason;
    private String status;
    @TableField(fill = FieldFill.INSERT) private LocalDateTime createTime;
}
