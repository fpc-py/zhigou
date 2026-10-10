package com.zhigou.wallet.service;

import com.zhigou.wallet.entity.WalletAccount;
import com.zhigou.wallet.entity.WalletTransaction;

import java.util.List;
import java.util.Map;

public interface WalletService {
    /** 我的钱包账户（不存在则惰性创建） */
    WalletAccount myAccount(Long userId);
    /** 沙箱充值（幂等：同一 bizNo 只入账一次） */
    WalletTransaction recharge(Long userId, Long amountFen, String bizNo, String remark);
    /** 流水分页 */
    List<WalletTransaction> transactions(Long userId, long pageNum, long pageSize);
    /** 会员等级与权益（静态权益说明） */
    Map<String, Object> memberLevel(Long userId);
}
