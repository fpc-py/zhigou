package com.zhigou.marketing.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("seckill_activity")
public class SeckillActivity {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long skuId;
    private String title;
    private String imageUrl;
    /** 秒杀价(分) */
    private Long seckillPrice;
    /** 原价(分) */
    private Long originPrice;
    /** 总库存 */
    private Integer total;
    /** 已售（原子自增防超卖） */
    private Integer sold;
    /** 每人限购件数 */
    private Integer perUserLimit;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private Integer status;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
    @Version
    private Integer version;
    @TableLogic
    private Integer deleted;
}
