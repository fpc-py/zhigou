package com.zhigou.payment.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

/**
 * 退款单。沙箱环境退款即时成功（status=SUCCESS）；生产替换真实渠道后
 * 先置 REFUNDING，收到渠道回调/对账后置 SUCCESS。
 */
@Data @TableName("payment_refund")
public class PaymentRefund {
    @TableId(type = IdType.AUTO) private Long id;
    /** 退款单号（幂等键） */
    private String refundNo;
    /** 原支付单号 */
    private String paymentNo;
    /** 业务订单号 */
    private String orderNo;
    private Long userId;
    /** 退款金额（分） */
    private Long amount;
    /** REFUNDING / SUCCESS / FAILED */
    private String status;
    private String reason;
    private LocalDateTime refundedAt;
    @TableField(fill = FieldFill.INSERT) private LocalDateTime createTime;
    @TableField(fill = FieldFill.INSERT_UPDATE) private LocalDateTime updateTime;
    @Version private Integer version;
    @TableLogic private Integer deleted;
}
