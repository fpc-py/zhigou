package com.zhigou.order.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data @TableName("outbox")
public class Outbox {
    @TableId(type = IdType.AUTO) private Long id;
    private String messageId;
    private String topic;
    private String tag;
    private String payload;
    private Integer status; // 0待发送 1已发送
    @TableField(fill = FieldFill.INSERT) private LocalDateTime createTime;
}