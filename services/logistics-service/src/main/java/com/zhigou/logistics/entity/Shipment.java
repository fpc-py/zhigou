package com.zhigou.logistics.entity;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;
@Data @TableName("shipment")
public class Shipment {
    @JsonSerialize(using = ToStringSerializer.class)
    @TableId(type = IdType.AUTO) private Long id;
    
    @JsonSerialize(using = ToStringSerializer.class) private Long userId;
    private String shipmentNo; private String orderNo; private String senderAddr; private String receiverAddr; private Integer weightG; private Long freightFee; private String carrier; private String status; private java.time.LocalDateTime shippedAt; private java.time.LocalDateTime deliveredAt;
    
    @TableField(fill = FieldFill.INSERT) private LocalDateTime createTime;
    @TableField(fill = FieldFill.INSERT_UPDATE) private LocalDateTime updateTime; @Version private Integer version; @TableLogic private Integer deleted;
}
