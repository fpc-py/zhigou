package com.zhigou.wallet.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zhigou.common.BizException;
import com.zhigou.wallet.entity.WalletAccount;
import com.zhigou.wallet.entity.WalletTransaction;
import com.zhigou.wallet.mapper.WalletAccountMapper;
import com.zhigou.wallet.mapper.WalletTransactionMapper;
import com.zhigou.wallet.service.WalletService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class WalletServiceImpl implements WalletService {

    private final WalletAccountMapper accountMapper;
    private final WalletTransactionMapper txnMapper;

    @Override
    public WalletAccount myAccount(Long userId) {
        if (userId == null) throw new BizException(40001, "请先登录");
        WalletAccount acc = accountMapper.selectOne(
                new LambdaQueryWrapper<WalletAccount>().eq(WalletAccount::getUserId, userId));
        if (acc == null) {
            acc = new WalletAccount();
            acc.setUserId(userId);
            acc.setBalanceFen(0L);
            acc.setTotalRechargeFen(0L);
            acc.setTotalConsumeFen(0L);
            acc.setPoints(0);
            acc.setTotalPoints(0);
            acc.setMemberLevel("FREE");
            acc.setStatus(1);
            acc.setCreatedAt(LocalDateTime.now());
            acc.setUpdatedAt(LocalDateTime.now());
            accountMapper.insert(acc);
        }
        return acc;
    }

    @Override
    @Transactional
    public WalletTransaction recharge(Long userId, Long amountFen, String bizNo, String remark) {
        WalletAccount acc = myAccount(userId);
        if (amountFen == null || amountFen <= 0) throw new BizException(40041, "充值金额必须大于 0");
        if (amountFen > 1_000_000L) throw new BizException(40042, "单次充值金额超上限（1 万元）");
        String no = (bizNo == null || bizNo.isBlank()) ? UUID.randomUUID().toString().replace("-", "") : bizNo.trim();
        // 幂等：同一业务单号已入账则直接返回原流水
        WalletTransaction exist = txnMapper.selectOne(
                new LambdaQueryWrapper<WalletTransaction>()
                        .eq(WalletTransaction::getBizNo, no)
                        .eq(WalletTransaction::getUserId, userId)
                        .last("LIMIT 1"));
        if (exist != null) return exist;

        long after = acc.getBalanceFen() + amountFen;
        // 演示积分规则：充值 1 元 = 1 积分
        int gain = (int) (amountFen / 100);
        acc.setBalanceFen(after);
        acc.setTotalRechargeFen(acc.getTotalRechargeFen() + amountFen);
        acc.setPoints(acc.getPoints() + gain);
        acc.setTotalPoints(acc.getTotalPoints() + gain);
        acc.setUpdatedAt(LocalDateTime.now());
        accountMapper.updateById(acc);

        WalletTransaction t = new WalletTransaction();
        t.setUserId(userId);
        t.setType("RECHARGE");
        t.setAmountFen(amountFen);
        t.setBalanceAfterFen(after);
        t.setBizNo(no);
        t.setRemark((remark == null || remark.isBlank() ? "沙箱充值" : remark) + (gain > 0 ? "，赠 " + gain + " 积分" : ""));
        t.setCreatedAt(LocalDateTime.now());
        txnMapper.insert(t);
        return t;
    }

    @Override
    public List<WalletTransaction> transactions(Long userId, long pageNum, long pageSize) {
        if (userId == null) throw new BizException(40001, "请先登录");
        long size = Math.min(Math.max(1, pageSize), 50);
        LambdaQueryWrapper<WalletTransaction> q = new LambdaQueryWrapper<WalletTransaction>()
                .eq(WalletTransaction::getUserId, userId)
                .orderByDesc(WalletTransaction::getCreatedAt)
                .last("limit " + Math.max(0, (pageNum - 1) * size) + "," + size);
        return txnMapper.selectList(q);
    }

    @Override
    @Transactional
    public Map<String, Object> subscribe(Long userId, String level) {
        WalletAccount acc = myAccount(userId);
        String lv = level == null ? "" : level.trim().toUpperCase();
        long price;
        switch (lv) {
            case "ADVANCED" -> price = 2900L; // ¥29 / 30 天
            case "FLAGSHIP" -> price = 9900L; // ¥99 / 30 天
            default -> throw new BizException(40043, "不支持的会员档位（ADVANCED/FLAGSHIP）");
        }
        if (acc.getBalanceFen() < price) throw new BizException(40044, "余额不足，请先充值（沙箱订阅）");
        LocalDateTime now = LocalDateTime.now();
        // 未到期续费顺延，否则从当前起算 30 天
        LocalDateTime base = acc.getMemberExpireAt() != null && acc.getMemberExpireAt().isAfter(now)
                ? acc.getMemberExpireAt() : now;
        acc.setBalanceFen(acc.getBalanceFen() - price);
        acc.setTotalConsumeFen(acc.getTotalConsumeFen() + price);
        acc.setMemberLevel(lv);
        acc.setMemberExpireAt(base.plusDays(30));
        acc.setUpdatedAt(now);
        accountMapper.updateById(acc);

        WalletTransaction t = new WalletTransaction();
        t.setUserId(userId);
        t.setType("CONSUME");
        t.setAmountFen(price);
        t.setBalanceAfterFen(acc.getBalanceFen());
        t.setBizNo("SUB" + now.format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS")) + userId % 10000);
        t.setRemark("开通" + (lv.equals("FLAGSHIP") ? "旗舰" : "高级") + "会员 30 天（沙箱订阅，扣余额 ¥" + (price / 100) + "）");
        t.setCreatedAt(now);
        txnMapper.insert(t);
        return subscription(userId);
    }

    @Override
    public Map<String, Object> subscription(Long userId) {
        WalletAccount acc = myAccount(userId);
        Map<String, Object> r = new HashMap<>();
        r.put("level", acc.getMemberLevel());
        LocalDateTime exp = acc.getMemberExpireAt();
        boolean active = exp != null ? exp.isAfter(LocalDateTime.now()) : !"FREE".equals(acc.getMemberLevel());
        r.put("expireAt", exp);
        r.put("active", active);
        r.put("benefits", memberLevel(userId).get("benefits"));
        r.put("note", "会员订阅为演示口径：沙箱扣余额，不接真实支付通道");
        return r;
    }

    @Override
    public Map<String, Object> memberLevel(Long userId) {
        WalletAccount acc = myAccount(userId);
        Map<String, Object> r = new HashMap<>();
        r.put("level", acc.getMemberLevel());
        r.put("title", acc.getMemberLevel().equals("FREE") ? "基础免费" : acc.getMemberLevel().equals("ADVANCED") ? "高级会员 ¥29/月" : "旗舰会员 ¥99/月");
        String[] benefits;
        switch (acc.getMemberLevel()) {
            case "FLAGSHIP" -> benefits = new String[]{"专属 AI 助理", "全网比价 + 砍价", "免运费券 12 张/月", "生日礼包"};
            case "ADVANCED" -> benefits = new String[]{"跨平台比价", "AI 砍价助手", "免运费券 6 张/月"};
            default -> benefits = new String[]{"基础 AI 问答", "社区种草", "本地生活预约"};
        }
        r.put("benefits", benefits);
        r.put("note", "会员订阅为演示口径，开通/支付功能规划中");
        return r;
    }
}
