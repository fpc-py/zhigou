package com.zhigou.logistics.entity;
import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;
@Data @TableName("track_event")
public class TrackEvent {
    @TableId(type = IdType.AUTO) private Long id;
    
    
    private String shipmentNo; private LocalDateTime nodeTime; private String nodeName; private String description;
    @TableField(fill = FieldFill.INSERT) private LocalDateTime createTime;
    
}
