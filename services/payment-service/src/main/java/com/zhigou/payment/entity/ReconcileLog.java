package com.zhigou.payment.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data @TableName("payment_reconcile_log")
public class ReconcileLog {
    @TableId(type = IdType.AUTO) private Long id;
    private LocalDate reconcileDate;
    private Integer totalCount;
    private Integer successCount;
    private Integer pendingCount;
    private Integer refundedCount;
    private Integer diffCount;
    private String summaryJson;
    private String diffJson;
    private Integer compensated;
    @TableField(fill = FieldFill.INSERT) private LocalDateTime createTime;
    @TableField(fill = FieldFill.INSERT_UPDATE) private LocalDateTime updateTime;
}
