package com.zhigou.product.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Builder;
import lombok.Data;
import java.time.LocalDateTime;

/** 评价列表项（P1 四批） */
@Data
@Builder
public class ReviewVO {
    /** Snowflake ID 序列化为字符串避免 JS 精度丢失 */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long reviewId;
    @JsonSerialize(using = ToStringSerializer.class)
    private Long spuId;
    /** 脱敏昵称（如 张**） */
    private String userName;
    private Integer rating;
    private String content;
    private String images;
    /** 1=演示数据 0=真实评价 */
    private Integer isMock;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "Asia/Shanghai")
    private LocalDateTime createTime;
}
