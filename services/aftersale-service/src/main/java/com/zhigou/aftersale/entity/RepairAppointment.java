package com.zhigou.aftersale.entity;
import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;
@Data @TableName("repair_appointment")
public class RepairAppointment {
    @TableId(type = IdType.AUTO) private Long id;
    private String appointmentNo; private String orderNo; private String skuId; private Long userId;
    private String productName; private String faultDesc; private String contactPhone;
    private LocalDateTime appointmentTime;
    private String status;
    @TableField(fill = FieldFill.INSERT) private LocalDateTime createTime;
    @TableField(fill = FieldFill.INSERT_UPDATE) private LocalDateTime updateTime;
    @Version private Integer version; @TableLogic private Integer deleted;
}
