package com.zhigou.order.entity;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data @TableName("outbox")
public class Outbox {
    @JsonSerialize(using = ToStringSerializer.class)
    @TableId(type = IdType.AUTO) private Long id;
    private String messageId;
    private String topic;
    private String tag;
    private String payload;
    private Integer status; // 0待发送 1已发送
    @TableField(fill = FieldFill.INSERT) private LocalDateTime createTime;
}