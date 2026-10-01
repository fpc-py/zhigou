package com.zhigou.order.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data @TableName("order_main")
public class OrderMain {
    @TableId(type = IdType.AUTO) private Long id;
    private Long orderId;
    private Long userId;
    private String requestId;
    private String orderStatus;
    private Long totalAmount;
    private Long payAmount;
    private Long couponId;
    private String receiverName;
    private String receiverPhone;
    private String receiverAddress;
    private String closeReason;
    @TableField(fill = FieldFill.INSERT) private LocalDateTime createTime;
    @TableField(fill = FieldFill.INSERT_UPDATE) private LocalDateTime updateTime;
    @Version private Integer version;
    @TableLogic private Integer deleted;
}