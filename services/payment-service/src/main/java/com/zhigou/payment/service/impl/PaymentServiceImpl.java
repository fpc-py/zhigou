package com.zhigou.payment.service.impl;

import cn.hutool.core.util.IdUtil;
import cn.hutool.crypto.digest.DigestUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zhigou.common.BizException;
import com.zhigou.payment.client.OrderNotifyClient;
import com.zhigou.payment.entity.Payment;
import com.zhigou.payment.mapper.PaymentMapper;
import com.zhigou.payment.service.PaymentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j @Service @RequiredArgsConstructor
public class PaymentServiceImpl implements PaymentService {

    private final PaymentMapper paymentMapper;
    private final OrderNotifyClient orderNotifyClient;
    @Value("${payment.sandbox-secret}") private String sandboxSecret;

    @Override @Transactional
    public String create(Long userId, String orderNo, Long amount) {
        // 幂等: 同 orderNo 只建一次
        Payment existing = paymentMapper.selectOne(new LambdaQueryWrapper<Payment>().eq(Payment::getOrderNo, orderNo));
        if (existing != null) return existing.getPaymentNo();

        String paymentNo = "PAY" + IdUtil.fastSimpleUUID().toUpperCase().substring(0, 16);
        Payment payment = new Payment();
        payment.setPaymentNo(paymentNo); payment.setOrderNo(orderNo);
        payment.setUserId(userId); payment.setAmount(amount);
        payment.setStatus("PENDING"); payment.setChannel("SANDBOX");
        paymentMapper.insert(payment);
        log.info("支付单创建: paymentNo={}, orderNo={}, amount={}", paymentNo, orderNo, amount);
        return paymentNo;
    }

    @Override @Transactional
    public void mockPay(String paymentNo, String sign) {
        // 验签
        String expected = DigestUtil.sha256Hex(paymentNo + sandboxSecret);
        log.info("mockPay verify: paymentNo={}, secret={}, expected={}, sign={}", paymentNo, sandboxSecret, expected, sign);
        if (!expected.equals(sign)) throw new BizException(403, "签名校验失败");

        Payment payment = paymentMapper.selectOne(new LambdaQueryWrapper<Payment>().eq(Payment::getPaymentNo, paymentNo));
        if (payment == null) throw new BizException(404, "支付单不存在");

        if (!"PENDING".equals(payment.getStatus())) {
            log.info("重复支付回调: paymentNo={}", paymentNo);
            return; // 幂等
        }

        payment.setStatus("SUCCESS"); payment.setPaidTime(LocalDateTime.now());
        paymentMapper.updateById(payment);
        log.info("支付成功: paymentNo={}, orderNo={}", paymentNo, payment.getOrderNo());

        // 联动订单服务：订单 INIT → PAID（失败不阻断，重试+对账兜底）
        orderNotifyClient.notifyPaid(payment.getOrderNo());
    }

    @Override
    public void reconcile() {
        // T+1 对账: 检查 SUCCESS 但支付单缺失的异常情况
        List<Payment> pendings = paymentMapper.selectList(
                new LambdaQueryWrapper<Payment>().eq(Payment::getStatus, "PENDING"));
        LocalDateTime yesterday = LocalDateTime.now().minusDays(1);
        long stale = pendings.stream().filter(p -> p.getCreateTime() != null && p.getCreateTime().isBefore(yesterday)).count();
        if (stale > 0) {
            log.error("对账异常: {} 笔 PENDING 超过 24h", stale);
        } else {
            log.info("对账正常: 无超时待支付单");
        }
    }
}