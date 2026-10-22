package com.zhigou.marketing.entity;
import com.baomidou.mybatisplus.annotation.*;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Data;
import java.time.LocalDateTime;
@Data @TableName("discount_snapshot")
public class DiscountSnapshot {
    @TableId(type = IdType.AUTO) private Long id;
    private String orderNo; private Long totalAmount; private Long discountAmount;
    @JsonSerialize(using = ToStringSerializer.class)
    private Long couponId; private String ruleIds; private Long finalAmount;
    private LocalDateTime createTime;
}
