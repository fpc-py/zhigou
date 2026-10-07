package com.zhigou.payment.controller;

import com.zhigou.common.Result;
import com.zhigou.payment.service.PaymentService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController @RequestMapping("/payment") @RequiredArgsConstructor
public class PaymentController {
    private final PaymentService paymentService;

    @PostMapping("/create")
    public Result<Map<String, String>> create(@RequestBody Map<String, Object> body) {
        // 兼容 userId 为字符串（BFF 透传）或数字两种形态
        Long userId = toLong(body.get("userId"));
        String orderNo = (String) body.get("orderNo");
        Long amount = toLong(body.get("amount"));
        if (userId == null || orderNo == null || amount == null) {
            throw new com.zhigou.common.BizException(400, "参数缺失: userId/orderNo/amount");
        }
        String pno = paymentService.create(userId, orderNo, amount);
        String qrUrl = "https://sandbox.pay.zhigou.com/qr?paymentNo=" + pno;
        return Result.ok(Map.of("paymentNo", pno, "qrUrl", qrUrl));
    }

    /** 兼容 Number 与 String 两种 JSON 数值形态 */
    private static Long toLong(Object v) {
        if (v == null) return null;
        if (v instanceof Number n) return n.longValue();
        if (v instanceof String s && !s.isBlank()) {
            try { return Long.valueOf(s.trim()); } catch (NumberFormatException ignore) { return null; }
        }
        return null;
    }

    @PostMapping("/sandbox/mock-pay")
    public Result<Void> mockPay(@RequestBody Map<String, String> body) {
        paymentService.mockPay(body.get("paymentNo"), body.get("sign"));
        return Result.ok();
    }

    /**
     * 退款（内网服务调用：aftersale-service 审核通过后发起）。
     * 沙箱即时成功；幂等——同订单重复退款只成功一次。生产应加内网白名单。
     */
    @PostMapping("/refund")
    public Result<Map<String, String>> refund(@RequestBody Map<String, Object> body) {
        String orderNo = (String) body.get("orderNo");
        Long amount = toLong(body.get("amount"));
        String reason = body.get("reason") == null ? null : String.valueOf(body.get("reason"));
        if (orderNo == null || amount == null) {
            throw new com.zhigou.common.BizException(400, "参数缺失: orderNo/amount");
        }
        String refundNo = paymentService.refund(orderNo, amount, reason);
        return Result.ok(Map.of("refundNo", refundNo));
    }

    /**
     * 运维触发 T+1 对账补偿（定时任务每日凌晨 1 点自动执行，此端点为手动补跑入口）。
     * 生产环境应增加内网白名单/管理员鉴权，演示环境放行。
     */
    @PostMapping("/reconcile")
    public Result<Map<String, Object>> reconcile() {
        paymentService.reconcile();
        return Result.ok(Map.of("triggered", true));
    }
}