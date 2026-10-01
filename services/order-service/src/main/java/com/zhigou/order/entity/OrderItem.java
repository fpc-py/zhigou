package com.zhigou.order.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data @TableName("order_item")
public class OrderItem {
    @TableId(type = IdType.AUTO) private Long id;
    private Long orderId;
    private Long skuId;
    private Long spuId;
    private String skuName;
    private String spec;
    private Long price;
    private Integer count;
    @TableField(fill = FieldFill.INSERT) private LocalDateTime createTime;
}