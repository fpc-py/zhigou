package com.zhigou.wallet.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Data;

import java.time.LocalDateTime;

/** 用户钱包账户 */
@Data
@TableName("wallet_account")
public class WalletAccount {
    @TableId(type = IdType.ASSIGN_ID)
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;
    @JsonSerialize(using = ToStringSerializer.class)
    private Long userId;
    /** 余额（分） */
    private Long balanceFen;
    /** 累计充值（分） */
    private Long totalRechargeFen;
    /** 累计消费（分） */
    private Long totalConsumeFen;
    /** 当前积分 */
    private Integer points;
    /** 累计获取积分 */
    private Integer totalPoints;
    /** FREE 基础免费 / ADVANCED 高级 ¥29 / FLAGSHIP 旗舰 ¥99 */
    private String memberLevel;
    private Integer status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
