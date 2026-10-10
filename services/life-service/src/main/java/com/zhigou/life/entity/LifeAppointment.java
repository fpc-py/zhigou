package com.zhigou.life.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Data;

import java.time.LocalDateTime;

/** 到店 / 服务预约单 */
@Data
@TableName("life_appointment")
public class LifeAppointment {
    @TableId(type = IdType.ASSIGN_ID)
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;
    @JsonSerialize(using = ToStringSerializer.class)
    private Long userId;
    @JsonSerialize(using = ToStringSerializer.class)
    private Long storeId;
    @JsonSerialize(using = ToStringSerializer.class)
    private Long skuId;
    private String appointmentTime;
    private String remark;
    /** 0=待确认 1=已确认 2=已完成 3=已取消 */
    private Integer status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
