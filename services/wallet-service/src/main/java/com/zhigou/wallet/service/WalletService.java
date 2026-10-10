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
    /** 开通/续费会员（沙箱扣余额，ADVANCED ¥29/30 天、FLAGSHIP ¥99/30 天，未到期顺延） */
    Map<String, Object> subscribe(Long userId, String level);
    /** 当前订阅状态（档位/到期/是否有效/权益） */
    Map<String, Object> subscription(Long userId);
}
