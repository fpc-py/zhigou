package com.zhigou.marketing.entity;
import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;
@Data @TableName("user_coupon")
public class UserCoupon {
    @TableId(type = IdType.AUTO) private Long id;
    private Long userId; private Long couponTemplateId; private String status;
    private Long sourceOrderId; private LocalDateTime lockedAt; private LocalDateTime usedAt;
    @TableField(fill = FieldFill.INSERT) private LocalDateTime createTime;
    @TableField(fill = FieldFill.INSERT_UPDATE) private LocalDateTime updateTime;
    @Version private Integer version; @TableLogic private Integer deleted;
}
