package com.zhigou.aftersale.dto;
import lombok.Data;
@Data
public class OrderInfo {
    private Long orderId; private String orderStatus; private Long payAmount; private Long userId;
}