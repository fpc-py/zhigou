package com.zhigou.order.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data @TableName("order_shipment")
public class OrderShipment {
    @TableId(type = IdType.AUTO) private Long id;
    private Long orderId;
    private Long skuId;
    private Integer count;
    private String trackingNo;
    private LocalDateTime shipTime;
    @TableField(fill = FieldFill.INSERT) private LocalDateTime createTime;
    @TableLogic private Integer deleted;
}
