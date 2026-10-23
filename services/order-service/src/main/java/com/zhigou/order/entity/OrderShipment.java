package com.zhigou.order.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Data;
import java.time.LocalDateTime;

@Data @TableName("order_shipment")
public class OrderShipment {
    @JsonSerialize(using = ToStringSerializer.class)
    @TableId(type = IdType.AUTO) private Long id;
    @JsonSerialize(using = ToStringSerializer.class)
    private Long orderId;
    @JsonSerialize(using = ToStringSerializer.class)
    private Long skuId;
    private Integer count;
    private String trackingNo;
    private LocalDateTime shipTime;
    @TableField(fill = FieldFill.INSERT) private LocalDateTime createTime;
    @TableLogic private Integer deleted;
}
