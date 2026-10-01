package com.zhigou.inventory.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data @TableName("stock")
public class Stock {
    @TableId(type = IdType.AUTO) private Long id;
    private Long skuId;
    private Integer available;
    private Integer locked;
    @Version private Integer version;
    @TableField(fill = FieldFill.INSERT) private LocalDateTime createTime;
    @TableField(fill = FieldFill.INSERT_UPDATE) private LocalDateTime updateTime;
}