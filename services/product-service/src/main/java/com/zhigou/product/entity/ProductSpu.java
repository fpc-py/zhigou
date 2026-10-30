package com.zhigou.product.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("product_spu")
public class ProductSpu {
    @TableId(type = IdType.AUTO)
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;
    @JsonSerialize(using = ToStringSerializer.class)
    private Long spuId;
    @JsonSerialize(using = ToStringSerializer.class)
    private Long categoryId;
    @JsonSerialize(using = ToStringSerializer.class)
    private Long brandId;
    /** 所属商家ID（多商家隔离；平台全局商品为 null） */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long merchantId;
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