package com.zhigou.payment.service.impl;

import cn.hutool.core.util.IdUtil;
import cn.hutool.crypto.digest.DigestUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zhigou.common.BizException;
import com.zhigou.payment.client.OrderNotifyClient;
import com.zhigou.payment.entity.Payment;
import com.zhigou.payment.entity.PaymentRefund;
import com.zhigou.payment.mapper.PaymentMapper;
import com.zhigou.payment.mapper.PaymentRefundMapper;
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
    private final PaymentRefundMapper refundMapper;
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
        boolean ok = orderNotifyClient.notifyPaid(payment.getOrderNo());
        if (ok) {
            payment.setNotifyStatus(1);
            paymentMapper.updateById(payment);
        } else {
            log.warn("订单联动通知失败，待 T+1 对账补偿: paymentNo={}", paymentNo);
        }
    }

    @Override @Transactional
    public String refund(String orderNo, Long amount, String reason) {
        // 1. 幂等：同订单已有退款单直接返回（避免重复退款）
        PaymentRefund existing = refundMapper.selectOne(new LambdaQueryWrapper<PaymentRefund>()
                .eq(PaymentRefund::getOrderNo, orderNo)
                .eq(PaymentRefund::getStatus, "SUCCESS"));
        if (existing != null) {
            log.info("退款幂等命中: orderNo={}, refundNo={}", orderNo, existing.getRefundNo());
            return existing.getRefundNo();
        }

        // 2. 校验原支付单：必须已支付成功
        Payment payment = paymentMapper.selectOne(new LambdaQueryWrapper<Payment>().eq(Payment::getOrderNo, orderNo));
        if (payment == null) throw new BizException(404, "支付单不存在: orderNo=" + orderNo);
        if (!"SUCCESS".equals(payment.getStatus())) throw new BizException(400, "订单未支付成功，不可退款");
        if (amount == null || amount <= 0 || amount > payment.getAmount())
            throw new BizException(400, "退款金额非法（0<amount<=实付金额）");

        // 3. 创建退款单。沙箱即时成功；生产替换渠道 SDK：先 REFUNDING，异步回调后置 SUCCESS
        String refundNo = "RF" + IdUtil.fastSimpleUUID().toUpperCase().substring(0, 16);
        PaymentRefund refund = new PaymentRefund();
        refund.setRefundNo(refundNo); refund.setPaymentNo(payment.getPaymentNo());
        refund.setOrderNo(orderNo); refund.setUserId(payment.getUserId());
        refund.setAmount(amount); refund.setReason(reason);
        refund.setStatus("SUCCESS"); refund.setRefundedAt(LocalDateTime.now());
        refundMapper.insert(refund);
        log.info("退款成功(沙箱): refundNo={}, paymentNo={}, orderNo={}, amount={}", refundNo, payment.getPaymentNo(), orderNo, amount);
        return refundNo;
    }

    @Override
    public void reconcile() {
        // 1) 补偿：SUCCESS 但未通知订单服务的支付单（前 3 次重试失败 → 对账兜底）
        List<Payment> unnotified = paymentMapper.selectList(new LambdaQueryWrapper<Payment>()
                .eq(Payment::getStatus, "SUCCESS")
                .eq(Payment::getNotifyStatus, 0));
        int compensated = 0;
        for (Payment p : unnotified) {
            try {
                if (orderNotifyClient.notifyPaid(p.getOrderNo())) {
                    p.setNotifyStatus(1);
                    paymentMapper.updateById(p);
                    compensated++;
                    log.info("对账补偿成功: paymentNo={}, orderNo={}", p.getPaymentNo(), p.getOrderNo());
                } else {
                    log.error("对账补偿失败（明日重试）: paymentNo={}, orderNo={}", p.getPaymentNo(), p.getOrderNo());
                }
            } catch (Exception e) {
                log.error("对账补偿异常（明日重试）: paymentNo={}, err={}", p.getPaymentNo(), e.getMessage());
            }
        }

        // 2) 风险告警：PENDING 超过 24h 的支付单（用户未完成支付，可联动超时关单）
        LocalDateTime yesterday = LocalDateTime.now().minusDays(1);
        long stale = paymentMapper.selectList(new LambdaQueryWrapper<Payment>()
                .eq(Payment::getStatus, "PENDING")).stream()
                .filter(p -> p.getCreateTime() != null && p.getCreateTime().isBefore(yesterday)).count();
        if (stale > 0) {
            log.error("对账异常: {} 笔 PENDING 超过 24h，需联动超时关单", stale);
        } else {
            log.info("对账正常: 无超时待支付单");
        }
        log.info("T+1 对账完成: 补偿 {} 笔订单联动通知", compensated);
    }
}