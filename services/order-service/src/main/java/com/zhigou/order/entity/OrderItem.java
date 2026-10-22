package com.zhigou.order.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Data;
import java.time.LocalDateTime;

@Data @TableName("order_item")
public class OrderItem {
    @TableId(type = IdType.AUTO) private Long id;
    @JsonSerialize(using = ToStringSerializer.class)
    private Long orderId;
    @JsonSerialize(using = ToStringSerializer.class)
    private Long skuId;
    @JsonSerialize(using = ToStringSerializer.class)
    private Long spuId;
    private String skuName;
    private String spec;
    private Long price;
    private Integer count;
    @TableField(fill = FieldFill.INSERT) private LocalDateTime createTime;
}