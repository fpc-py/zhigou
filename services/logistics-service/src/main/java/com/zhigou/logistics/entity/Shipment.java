package com.zhigou.logistics.entity;
import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;
@Data @TableName("shipment")
public class Shipment {
    @TableId(type = IdType.AUTO) private Long id;
    
    private String shipmentNo; private String orderNo; private Long userId; private String senderAddr; private String receiverAddr; private Integer weightG; private Long freightFee; private String carrier; private String status; private java.time.LocalDateTime shippedAt; private java.time.LocalDateTime deliveredAt;
    
    @TableField(fill = FieldFill.INSERT) private LocalDateTime createTime;
    @TableField(fill = FieldFill.INSERT_UPDATE) private LocalDateTime updateTime; @Version private Integer version; @TableLogic private Integer deleted;
}
