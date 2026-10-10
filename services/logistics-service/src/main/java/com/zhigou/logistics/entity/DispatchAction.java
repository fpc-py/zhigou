package com.zhigou.logistics.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data @TableName("dispatch_action")
public class DispatchAction {
    @TableId(type = IdType.AUTO) private Long id;
    private String shipmentNo;
    private String action;
    private String reason;
    private String status;
    @TableField(fill = FieldFill.INSERT) private LocalDateTime createTime;
}
