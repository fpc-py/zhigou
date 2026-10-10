package com.zhigou.life.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Data;

import java.time.LocalDateTime;

/** POI 门店 / 商圈（本地生活） */
@Data
@TableName("poi_store")
public class PoiStore {
    @TableId(type = IdType.ASSIGN_ID)
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;
    private String name;
    /** 商圈 / 餐饮 / 生鲜 / 家政 / 到店 */
    private String category;
    private String address;
    private Integer distanceM;
    private Long avgPriceFen;
    private Double rating;
    private String coverUrl;
    private String hours;
    private String tags;
    private Integer businessStatus; // 1=营业 0=休息
    /** 演示数据：真实商圈经纬度占位，不接入地图 */
    private String lat;
    private String lng;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
