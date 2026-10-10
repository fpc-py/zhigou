package com.zhigou.aftersale.entity;
import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;
@Data @TableName("warranty_info")
public class WarrantyInfo {
    @TableId(type = IdType.AUTO) private Long id;
    private String orderNo; private String skuId; private Long userId;
    private String productName;
    private LocalDateTime purchaseTime;
    private Integer warrantyMonths;
    private LocalDateTime expireTime;
    @TableField(fill = FieldFill.INSERT) private LocalDateTime createTime;
    @TableField(fill = FieldFill.INSERT_UPDATE) private LocalDateTime updateTime;
    @Version private Integer version; @TableLogic private Integer deleted;
}
