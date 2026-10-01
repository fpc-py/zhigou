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
        Long userId = ((Number) body.get("userId")).longValue();
        String orderNo = (String) body.get("orderNo");
        Long amount = ((Number) body.get("amount")).longValue();
        String pno = paymentService.create(userId, orderNo, amount);
        String qrUrl = "https://sandbox.pay.zhigou.com/qr?paymentNo=" + pno;
        return Result.ok(Map.of("paymentNo", pno, "qrUrl", qrUrl));
    }

    @PostMapping("/sandbox/mock-pay")
    public Result<Void> mockPay(@RequestBody Map<String, String> body) {
        paymentService.mockPay(body.get("paymentNo"), body.get("sign"));
        return Result.ok();
    }
}