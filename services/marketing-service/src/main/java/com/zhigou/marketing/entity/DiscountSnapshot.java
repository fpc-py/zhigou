package com.zhigou.marketing.entity;
import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;
@Data @TableName("discount_snapshot")
public class DiscountSnapshot {
    @TableId(type = IdType.AUTO) private Long id;
    private String orderNo; private Long totalAmount; private Long discountAmount;
    private Long couponId; private String ruleIds; private Long finalAmount;
    private LocalDateTime createTime;
}
