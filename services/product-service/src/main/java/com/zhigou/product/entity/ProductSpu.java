package com.zhigou.product.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("product_spu")
public class ProductSpu {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long spuId;
    private Long categoryId;
    private Long brandId;
    private String name;
    private String subtitle;
    private String mainImage;
    private String description;
    private Integer status;  // 0下架 1上架
    private Long priceMin;
    private Long priceMax;
    private Integer salesVolume;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
    @Version private Integer version;
    @TableLogic private Integer deleted;
}