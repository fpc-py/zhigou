package com.zhigou.marketing;

import com.zhigou.common.BizException;
import com.zhigou.marketing.dto.CalculateRequest;
import com.zhigou.marketing.dto.CalculateResponse;
import com.zhigou.marketing.entity.UserCoupon;
import com.zhigou.marketing.mapper.UserCouponMapper;
import com.zhigou.marketing.service.CouponService;
import com.zhigou.marketing.service.impl.MarketingServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test") @Testcontainers
@Import(TestSecurityConfig.class)
class MarketingServiceIntegrationTest {

    @Container static MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.0").withDatabaseName("zhigou").withUsername("test").withPassword("test").withStartupTimeout(Duration.ofSeconds(120));

    @Container static GenericContainer<?> redis = new GenericContainer<>("redis:7-alpine").withExposedPorts(6379).withStartupTimeout(Duration.ofSeconds(120)).waitingFor(Wait.forListeningPort());    @DynamicPropertySource static void cfg(DynamicPropertyRegistry r) { r.add("TEST_JDBC_URL", mysql::getJdbcUrl);
        r.add("spring.datasource.url", mysql::getJdbcUrl);
        r.add("spring.datasource.username", mysql::getUsername);
        r.add("spring.datasource.password", mysql::getPassword);
        r.add("spring.data.redis.host", redis::getHost);
        r.add("spring.data.redis.port", () -> redis.getMappedPort(6379));
    }

    @Autowired private MarketingServiceImpl marketingService;
    @Autowired private CouponService couponService;
    @Autowired private UserCouponMapper ucMapper;
    @Autowired private JdbcTemplate jdbc;

    @BeforeEach void setUp() { jdbc.execute("DELETE FROM user_coupon"); jdbc.execute("DELETE FROM discount_snapshot"); }

    private CalculateRequest makeReq(long amount) {
        CalculateRequest r = new CalculateRequest(); r.setUserId(10001L);
        r.setItems(List.of(item(amount)));
        return r;
    }
    private CalculateRequest.Item item(long price) {
        CalculateRequest.Item i = new CalculateRequest.Item(); i.setSkuId(1L); i.setCount(1); i.setPrice(price);
        return i;
    }

    // 1: 满200减30命中
    @Test void shouldHitFullReduceRule() {
        CalculateResponse r = marketingService.calculate(makeReq(20000L));
        assertThat(r.getDiscountAmount()).isEqualTo(3000L);
        assertThat(r.getFinalAmount()).isEqualTo(17000L);
    }

    // 2: 不满门槛不命中
    @Test void shouldNotHitBelowThreshold() {
        CalculateResponse r = marketingService.calculate(makeReq(15000L));
        assertThat(r.getDiscountAmount()).isEqualTo(0L);
    }

    // 3: pre-freeze → FROZEN
    @Test void shouldFreezeCoupon() {
        Long cid = couponService.issue(10001L, 1L);
        couponService.preFreeze(10001L, cid, 999L);
        UserCoupon uc = ucMapper.selectById(cid);
        assertThat(uc.getStatus()).isEqualTo("FROZEN");
        assertThat(uc.getSourceOrderId()).isEqualTo(999L);
    }

    // 4: ORDER_PAID 核销 (confirm)
    @Test void shouldConfirmCoupon() {
        Long cid = couponService.issue(10001L, 1L);
        couponService.preFreeze(10001L, cid, 888L);
        couponService.confirm(888L);
        assertThat(ucMapper.selectById(cid).getStatus()).isEqualTo("USED");
    }

    // 5: 重复 confirm 幂等 (已 USED 的不再处理)
    @Test void shouldBeIdempotentOnDuplicateConfirm() {
        Long cid = couponService.issue(10001L, 1L);
        couponService.preFreeze(10001L, cid, 777L);
        couponService.confirm(777L);
        couponService.confirm(777L); // 重复——不报错
        assertThat(ucMapper.selectById(cid).getStatus()).isEqualTo("USED");
    }

    // 6: ORDER_CLOSED 释放
    @Test void shouldReleaseCouponOnClose() {
        Long cid = couponService.issue(10001L, 1L);
        couponService.preFreeze(10001L, cid, 666L);
        couponService.release(666L);
        assertThat(ucMapper.selectById(cid).getStatus()).isEqualTo("UNUSED");
    }

    // 7: 超限发券被拒
    @Test void shouldRejectOverLimitIssue() {
        couponService.issue(10001L, 2L); // per_user_limit=1
        assertThatThrownBy(() -> couponService.issue(10001L, 2L))
                .isInstanceOf(BizException.class).extracting("code").isEqualTo(40030);
    }

    // 8: 多次计算结果稳定
    @Test void shouldBeStableOnMultipleCalls() {
        CalculateResponse r1 = marketingService.calculate(makeReq(20000L));
        CalculateResponse r2 = marketingService.calculate(makeReq(20000L));
        assertThat(r1.getDiscountAmount()).isEqualTo(r2.getDiscountAmount());
        assertThat(r1.getFinalAmount()).isEqualTo(r2.getFinalAmount());
    }
}