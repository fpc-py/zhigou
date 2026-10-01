package com.zhigou.marketing.entity;
import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;
@Data @TableName("coupon_template")
public class CouponTemplate {
    @TableId(type = IdType.AUTO) private Long id;
    private String name; private String type;
    private Long thresholdAmount; private Long discountValue;
    private Integer totalCount; private Integer perUserLimit;
    private LocalDateTime validStart; private LocalDateTime validEnd; private Integer status;
    @TableField(fill = FieldFill.INSERT) private LocalDateTime createTime;
    @TableField(fill = FieldFill.INSERT_UPDATE) private LocalDateTime updateTime;
    @Version private Integer version; @TableLogic private Integer deleted;
}
