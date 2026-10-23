package com.zhigou.payment.entity;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data @TableName("payment_reconcile_log")
public class ReconcileLog {
    @JsonSerialize(using = ToStringSerializer.class)
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
