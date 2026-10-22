package com.zhigou.aftersale.dto;
import lombok.Data;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
@Data
public class OrderInfo {
    @JsonSerialize(using = ToStringSerializer.class)
    private Long orderId; private String orderStatus; private Long payAmount; private Long userId;
}