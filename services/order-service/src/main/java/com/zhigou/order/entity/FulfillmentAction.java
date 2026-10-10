package com.zhigou.order.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data @TableName("fulfillment_action")
public class FulfillmentAction {
    @TableId(type = IdType.AUTO) private Long id;
    private Long orderId;
    private Long skuId;
    private String action;
    private String reason;
    private String status;
    @TableField(fill = FieldFill.INSERT) private LocalDateTime createTime;
}
