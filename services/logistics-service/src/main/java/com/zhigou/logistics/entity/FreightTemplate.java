package com.zhigou.logistics.entity;
import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;
@Data @TableName("freight_template")
public class FreightTemplate {
    @TableId(type = IdType.AUTO) private Long id;
    private String name; private Integer firstWeightG; private Long firstFee; private Integer continuedWeightG; private Long continuedFee; private Long freeThresholdAmount; private String regionJson;
    
    
    @TableField(fill = FieldFill.INSERT) private LocalDateTime createTime;
    @TableField(fill = FieldFill.INSERT_UPDATE) private LocalDateTime updateTime; @Version private Integer version; @TableLogic private Integer deleted;
}
