package com.zhigou.payment.service.impl;

import cn.hutool.core.util.IdUtil;
import cn.hutool.crypto.digest.DigestUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zhigou.common.BizException;
import com.zhigou.payment.client.OrderNotifyClient;
import com.zhigou.payment.entity.Payment;
import com.zhigou.payment.entity.PaymentRefund;
import com.zhigou.payment.entity.ReconcileLog;
import com.zhigou.payment.mapper.PaymentMapper;
import com.zhigou.payment.mapper.PaymentRefundMapper;
import com.zhigou.payment.service.PaymentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Slf4j @Service @RequiredArgsConstructor
public class PaymentServiceImpl implements PaymentService {

    private final PaymentMapper paymentMapper;
    private final PaymentRefundMapper refundMapper;
    private final com.zhigou.payment.mapper.ReconcileLogMapper reconcileLogMapper;
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
        // 1. 幂等：同订单同金额的退款单直接返回（部分退款多笔金额各异，幂等粒度 = orderNo+amount）
        PaymentRefund existing = refundMapper.selectOne(new LambdaQueryWrapper<PaymentRefund>()
                .eq(PaymentRefund::getOrderNo, orderNo)
                .eq(PaymentRefund::getAmount, amount)
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
    public Map<String, Object> reconcile() {
        LocalDate today = LocalDate.now();
        List<Payment> all = paymentMapper.selectList(null);
        long success = all.stream().filter(p -> "SUCCESS".equals(p.getStatus())).count();
        long pending = all.stream().filter(p -> "PENDING".equals(p.getStatus())).count();
        long refunded = refundMapper.selectCount(new LambdaQueryWrapper<com.zhigou.payment.entity.PaymentRefund>()
                .eq(com.zhigou.payment.entity.PaymentRefund::getStatus, "SUCCESS"));

        // 差异清单：UNNOTIFIED（SUCCESS 未通知订单）/ STALE_PENDING（超 24h 未支付）/ OVER_REFUND（累计退款超实付）
        List<Map<String, String>> diffs = new java.util.ArrayList<>();
        List<Payment> unnotified = all.stream()
                .filter(p -> "SUCCESS".equals(p.getStatus()) && (p.getNotifyStatus() == null || p.getNotifyStatus() == 0))
                .collect(java.util.stream.Collectors.toList());
        for (Payment p : unnotified) {
            Map<String, String> d = new java.util.HashMap<>();
            d.put("type", "UNNOTIFIED"); d.put("paymentNo", p.getPaymentNo());
            d.put("orderNo", p.getOrderNo()); d.put("desc", "支付成功但订单未收到通知");
            diffs.add(d);
        }
        LocalDateTime yesterday = LocalDateTime.now().minusDays(1);
        for (Payment p : all) {
            if ("PENDING".equals(p.getStatus()) && p.getCreateTime() != null && p.getCreateTime().isBefore(yesterday)) {
                Map<String, String> d = new java.util.HashMap<>();
                d.put("type", "STALE_PENDING"); d.put("paymentNo", p.getPaymentNo());
                d.put("orderNo", p.getOrderNo()); d.put("desc", "PENDING 超 24h，需联动超时关单");
                diffs.add(d);
            }
        }
        // 超退核对：每笔支付单累计退款 > 实付金额
        for (Payment p : all) {
            long refundedSum = refundMapper.selectList(new LambdaQueryWrapper<com.zhigou.payment.entity.PaymentRefund>()
                            .eq(com.zhigou.payment.entity.PaymentRefund::getOrderNo, p.getOrderNo())
                            .eq(com.zhigou.payment.entity.PaymentRefund::getStatus, "SUCCESS"))
                    .stream().mapToLong(com.zhigou.payment.entity.PaymentRefund::getAmount).sum();
            if (refundedSum > p.getAmount()) {
                Map<String, String> d = new java.util.HashMap<>();
                d.put("type", "OVER_REFUND"); d.put("paymentNo", p.getPaymentNo());
                d.put("orderNo", p.getOrderNo());
                d.put("desc", "累计退款 " + refundedSum + " 超实付 " + p.getAmount() + "，需人工核查");
                diffs.add(d);
            }
        }

        // 补偿动作：UNNOTIFIED → 重发订单通知
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

        // 落库对账日志（按周期幂等：存在则更新）
        Map<String, Object> summary = new java.util.HashMap<>();
        summary.put("totalCount", all.size());
        summary.put("successCount", success);
        summary.put("pendingCount", pending);
        summary.put("refundedCount", refunded);
        summary.put("compensated", compensated);
        ReconcileLog logRow = reconcileLogMapper.selectOne(new LambdaQueryWrapper<ReconcileLog>()
                .eq(ReconcileLog::getReconcileDate, today));
        if (logRow == null) logRow = new ReconcileLog();
        logRow.setReconcileDate(today);
        logRow.setTotalCount(all.size());
        logRow.setSuccessCount((int) success);
        logRow.setPendingCount((int) pending);
        logRow.setRefundedCount((int) refunded);
        logRow.setDiffCount(diffs.size());
        logRow.setSummaryJson(cn.hutool.json.JSONUtil.toJsonStr(summary));
        logRow.setDiffJson(cn.hutool.json.JSONUtil.toJsonStr(diffs));
        logRow.setCompensated(compensated);
        if (logRow.getId() == null) reconcileLogMapper.insert(logRow); else reconcileLogMapper.updateById(logRow);

        log.info("T+1 对账完成: 汇总 total={} success={} pending={} refunded={}, 差异 {} 笔, 补偿 {} 笔",
                all.size(), success, pending, refunded, diffs.size(), compensated);

        Map<String, Object> report = new java.util.LinkedHashMap<>();
        report.put("reconcileDate", today.toString());
        report.put("summary", summary);
        report.put("diffList", diffs);
        report.put("compensated", compensated);
        return report;
    }
}