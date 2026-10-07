package com.zhigou.payment.entity;
import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data @TableName("payment")
public class Payment {
    @TableId(type = IdType.AUTO) private Long id;
    private String paymentNo; private String orderNo; private Long userId;
    private Long amount; private String status; private String channel;
    /** 订单联动通知状态：0=未通知 1=已通知（T+1 对账据此补偿） */
    private Integer notifyStatus;
    private LocalDateTime paidTime;
    @TableField(fill = FieldFill.INSERT) private LocalDateTime createTime;
    @TableField(fill = FieldFill.INSERT_UPDATE) private LocalDateTime updateTime;
    @Version private Integer version; @TableLogic private Integer deleted;
}