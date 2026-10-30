package com.zhigou.order.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import com.zhigou.common.mybatis.AmountTypeHandler;
import lombok.Data;
import org.apache.ibatis.type.JdbcType;
import java.time.LocalDateTime;

@Data @TableName("order_main")
public class OrderMain {
    @JsonSerialize(using = ToStringSerializer.class)
    @TableId(type = IdType.AUTO) private Long id;
    @JsonSerialize(using = ToStringSerializer.class)
    private Long orderId;
    @JsonSerialize(using = ToStringSerializer.class)
    private Long userId;
    /** 订单归属商家ID（多商家隔离；平台聚合订单为 null） */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long merchantId;
    private String requestId;
    private String orderStatus;
    @TableField(typeHandler = AmountTypeHandler.class, jdbcType = JdbcType.VARCHAR)
    private Long totalAmount;
    @TableField(typeHandler = AmountTypeHandler.class, jdbcType = JdbcType.VARCHAR)
    private Long payAmount;
    @JsonSerialize(using = ToStringSerializer.class)
    private Long couponId;
    private String receiverName;
    private String receiverPhone;
    private String receiverAddress;
    private String closeReason;
    @TableField(fill = FieldFill.INSERT) private LocalDateTime createTime;
    @TableField(fill = FieldFill.INSERT_UPDATE) private LocalDateTime updateTime;
    @Version private Integer version;
    @TableLogic private Integer deleted;
}