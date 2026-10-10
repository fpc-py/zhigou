package com.zhigou.wallet.controller;

import com.zhigou.common.Result;
import com.zhigou.wallet.entity.WalletAccount;
import com.zhigou.wallet.entity.WalletTransaction;
import com.zhigou.wallet.service.WalletService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping(produces = "application/json")
@RequiredArgsConstructor
public class WalletController {

    private final WalletService walletService;

    /** 我的钱包账户 */
    @GetMapping("/wallet/account")
    public Result<WalletAccount> account(@RequestParam Long userId) {
        return Result.ok(walletService.myAccount(userId));
    }

    /** 沙箱充值（金额分；bizNo 幂等，重复提交不重复入账） */
    @PostMapping("/wallet/recharge")
    public Result<WalletTransaction> recharge(@RequestBody Map<String, Object> body) {
        return Result.ok(walletService.recharge(
                Long.valueOf(String.valueOf(body.get("userId"))),
                body.get("amountFen") == null ? null : Long.valueOf(String.valueOf(body.get("amountFen"))),
                (String) body.get("bizNo"),
                (String) body.get("remark")));
    }

    /** 流水分页 */
    @GetMapping("/wallet/transactions")
    public Result<List<WalletTransaction>> transactions(@RequestParam Long userId,
                                                         @RequestParam(defaultValue = "1") long pageNum,
                                                         @RequestParam(defaultValue = "20") long pageSize) {
        return Result.ok(walletService.transactions(userId, pageNum, pageSize));
    }

    /** 会员等级与权益说明 */
    @GetMapping("/wallet/level")
    public Result<Map<String, Object>> level(@RequestParam Long userId) {
        return Result.ok(walletService.memberLevel(userId));
    }

    /** 开通/续费会员（沙箱扣余额，演示） */
    @PostMapping("/wallet/subscribe")
    public Result<Map<String, Object>> subscribe(@RequestBody Map<String, Object> body) {
        return Result.ok(walletService.subscribe(
                Long.valueOf(String.valueOf(body.get("userId"))),
                (String) body.get("level")));
    }

    /** 当前订阅状态 */
    @GetMapping("/wallet/subscription")
    public Result<Map<String, Object>> subscription(@RequestParam Long userId) {
        return Result.ok(walletService.subscription(userId));
    }
}
