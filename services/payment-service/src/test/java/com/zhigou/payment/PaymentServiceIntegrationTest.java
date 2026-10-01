package com.zhigou.payment;

import cn.hutool.crypto.digest.DigestUtil;
import com.zhigou.common.BizException;
import com.zhigou.payment.entity.Payment;
import com.zhigou.payment.mapper.PaymentMapper;
import com.zhigou.payment.service.PaymentService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Testcontainers
class PaymentServiceIntegrationTest {

    @Container static MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.0").withDatabaseName("zhigou").withUsername("test").withPassword("test");
    @DynamicPropertySource static void cfg(DynamicPropertyRegistry r) { r.add("TEST_JDBC_URL", mysql::getJdbcUrl); }

    @Autowired private PaymentService paymentService;
    @Autowired private PaymentMapper paymentMapper;
    @Autowired private JdbcTemplate jdbc;

    @BeforeEach void setUp() { jdbc.execute("DELETE FROM payment"); }

    // 1: 创建支付单
    @Test void shouldCreatePayment() {
        String pno = paymentService.create(10001L, "ORD001", 9900L);
        assertThat(pno).isNotNull().startsWith("PAY");

        Payment p = paymentMapper.selectOne(new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<Payment>().eq(Payment::getPaymentNo, pno));
        assertThat(p.getStatus()).isEqualTo("PENDING");
    }

    // 2: 重复创建幂等
    @Test void shouldReturnSamePaymentNoOnDuplicate() {
        String p1 = paymentService.create(10001L, "ORD002", 9900L);
        String p2 = paymentService.create(10001L, "ORD002", 9900L);
        assertThat(p1).isEqualTo(p2);
    }

    // 3: 模拟支付成功
    @Test void shouldMockPaySuccessfully() {
        String pno = paymentService.create(10001L, "ORD003", 9900L);
        String sign = DigestUtil.sha256Hex(pno + "test-sandbox-secret");
        paymentService.mockPay(pno, sign);
        Payment p = paymentMapper.selectOne(new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<Payment>().eq(Payment::getPaymentNo, pno));
        assertThat(p.getStatus()).isEqualTo("SUCCESS");
        assertThat(p.getPaidTime()).isNotNull();
    }

    // 4: 签名错误
    @Test void shouldRejectBadSignature() {
        String pno = paymentService.create(10001L, "ORD004", 9900L);
        assertThatThrownBy(() -> paymentService.mockPay(pno, "wrong-sign"))
                .isInstanceOf(BizException.class).extracting("code").isEqualTo(403);
    }

    // 5: 重复回调幂等
    @Test void shouldHandleDuplicateMockPay() {
        String pno = paymentService.create(10001L, "ORD005", 9900L);
        String sign = DigestUtil.sha256Hex(pno + "test-sandbox-secret");
        paymentService.mockPay(pno, sign);
        paymentService.mockPay(pno, sign); // 重复——不报错
        assertThat(paymentMapper.selectCount(null)).isEqualTo(1);
    }

    // 6: 对账不报错
    @Test void shouldReconcileWithoutError() {
        paymentService.create(10001L, "ORD006", 9900L);
        paymentService.reconcile(); // 刚建的不会超24h，正常对账
    }

    // 7: 支付失败的签名
    @Test void shouldRejectEmptyPaymentNo() {
        assertThatThrownBy(() -> paymentService.mockPay("", "x"))
                .isInstanceOf(BizException.class);
    }
}