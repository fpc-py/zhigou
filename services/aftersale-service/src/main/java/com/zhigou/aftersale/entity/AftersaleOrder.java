package com.zhigou.aftersale.entity;
import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;
@Data @TableName("aftersale_order")
public class AftersaleOrder {
    @TableId(type = IdType.AUTO) private Long id;
    private String aftersaleNo; private String orderNo; private Long userId;
    private String type; private String reason; private Long amount; private String status;
    private String images; private String rejectReason;
    private LocalDateTime applyAt; private LocalDateTime finishAt;
    @TableField(fill = FieldFill.INSERT) private LocalDateTime createTime;
    @TableField(fill = FieldFill.INSERT_UPDATE) private LocalDateTime updateTime;
    @Version private Integer version; @TableLogic private Integer deleted;
}