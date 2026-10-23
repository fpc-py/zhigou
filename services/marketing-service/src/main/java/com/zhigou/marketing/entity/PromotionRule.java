package com.zhigou.marketing.entity;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;
@Data @TableName("promotion_rule")
public class PromotionRule {
    @JsonSerialize(using = ToStringSerializer.class)
    @TableId(type = IdType.AUTO) private Long id;
    private String name; private String ruleContent; private Integer priority; private Integer status;
    private LocalDateTime startTime; private LocalDateTime endTime;
    @TableField(fill = FieldFill.INSERT) private LocalDateTime createTime;
    @Version private Integer version; @TableLogic private Integer deleted;
}
