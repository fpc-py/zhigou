package com.zhigou.wallet.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Data;

import java.time.LocalDateTime;

/** 钱包流水 */
@Data
@TableName("wallet_transaction")
public class WalletTransaction {
    @TableId(type = IdType.ASSIGN_ID)
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;
    @JsonSerialize(using = ToStringSerializer.class)
    private Long userId;
    /** RECHARGE 充值 / CONSUME 消费 / REFUND 退款 */
    private String type;
    /** 金额（分） */
    private Long amountFen;
    /** 变动后余额（分） */
    private Long balanceAfterFen;
    /** 业务单号（幂等） */
    private String bizNo;
    private String remark;
    private LocalDateTime createdAt;
}
