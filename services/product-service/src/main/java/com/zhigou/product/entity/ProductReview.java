package com.zhigou.product.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

/**
 * 商品评价（P1 四批：评价数据底座）。
 * reviewId 为 Snowflake ID（19 位），对外序列化需转字符串（CLAUDE.md 红线）。
 */
@Data
@TableName("product_review")
public class ProductReview {
    @TableId(type = IdType.AUTO)
    private Long id;
    /** 业务评价 ID（Snowflake） */
    private Long reviewId;
    /** 商品 SPU ID（Snowflake） */
    private Long spuId;
    /** 评价用户 ID（Snowflake） */
    private Long userId;
    /** 脱敏昵称 */
    private String userName;
    /** 1-5 星 */
    private Integer rating;
    /** 评价正文 */
    private String content;
    /** 晒图 URL，逗号分隔 */
    private String images;
    /** 1=演示数据 0=真实评价 */
    private Integer isMock;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
    @TableLogic private Integer deleted;
}
