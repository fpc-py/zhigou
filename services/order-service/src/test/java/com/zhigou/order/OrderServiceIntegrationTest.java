package com.zhigou.order;

import com.zhigou.common.BizException;
import com.zhigou.order.dto.CreateOrderRequest;
import com.zhigou.order.dto.OrderResponse;
import com.zhigou.order.entity.Outbox;
import com.zhigou.order.mapper.OutboxMapper;
import com.zhigou.order.service.OrderService;
import com.zhigou.order.state.OrderState;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Testcontainers
class OrderServiceIntegrationTest {

    @Container static MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.0").withDatabaseName("zhigou").withUsername("test").withPassword("test");
    @Container static GenericContainer<?> redis = new GenericContainer<>("redis:7-alpine").withExposedPorts(6379);
    @DynamicPropertySource static void cfg(DynamicPropertyRegistry r) {
        r.add("TEST_JDBC_URL", mysql::getJdbcUrl);
        r.add("spring.data.redis.host", redis::getHost);
        r.add("spring.data.redis.port", () -> redis.getMappedPort(6379));
    }

    @Autowired private OrderService orderService;
    @Autowired private OutboxMapper outboxMapper;
    @Autowired private JdbcTemplate jdbc;
    private static final Long USER = 10001L;

    @BeforeEach void setUp() { jdbc.execute("DELETE FROM outbox"); jdbc.execute("DELETE FROM order_item"); jdbc.execute("DELETE FROM order_main"); }

    private CreateOrderRequest newReq() { return newReq(UUID.randomUUID().toString().replace("-", "")); }
    private CreateOrderRequest newReq(String rid) {
        CreateOrderRequest r = new CreateOrderRequest(); r.setRequestId(rid);
        CreateOrderRequest.SkuItem si = new CreateOrderRequest.SkuItem(); si.setSkuId(1L); si.setCount(2);
        r.setSkuItems(List.of(si)); return r;
    }

    // 1: 正常下单
    @Test void shouldCreateOrderSuccessfully() {
        OrderResponse r = orderService.create(USER, newReq());
        assertThat(r.getOrderId()).isNotNull();
        assertThat(r.getOrderStatus()).isEqualTo("INIT");
        assertThat(r.getItems()).hasSize(1);
    }

    // 2: 幂等
    @Test void shouldDeduplicateByRequestId() {
        String rid = UUID.randomUUID().toString().replace("-","");
        OrderResponse r1 = orderService.create(USER, newReq(rid));
        OrderResponse r2 = orderService.create(USER, newReq(rid));
        assertThat(r1.getOrderId()).isEqualTo(r2.getOrderId());
    }

    // 3: 非法状态跃迁 PAID→PAID 应抛 40050
    @Test void shouldFailInvalidStateTransition() {
        OrderResponse r = orderService.create(USER, newReq());
        orderService.payCallback(r.getOrderId()); // INIT→PAID
        assertThatThrownBy(() -> orderService.payCallback(r.getOrderId()))
                .isInstanceOf(BizException.class).extracting("code").isEqualTo(40050);
    }

    // 4: INIT→PAID
    @Test void shouldTransitionInitToPaid() {
        OrderResponse r = orderService.create(USER, newReq());
        orderService.payCallback(r.getOrderId());
        assertThat(orderService.getByOrderId(r.getOrderId()).getOrderStatus()).isEqualTo("PAID");
    }

    // 5: 取消
    @Test void shouldCancelOrder() {
        OrderResponse r = orderService.create(USER, newReq());
        orderService.cancel(USER, r.getOrderId());
        assertThat(orderService.getByOrderId(r.getOrderId()).getOrderStatus()).isEqualTo("CLOSED");
    }

    // 6: PAID 不能取消
    @Test void shouldFailCancelPaidOrder() {
        OrderResponse r = orderService.create(USER, newReq());
        orderService.payCallback(r.getOrderId());
        assertThatThrownBy(() -> orderService.cancel(USER, r.getOrderId()))
                .isInstanceOf(BizException.class).extracting("code").isEqualTo(40050);
    }

    // 7: 下单 outbox
    @Test void shouldWriteOutboxOnCreate() {
        orderService.create(USER, newReq());
        assertThat(outboxMapper.selectCount(null)).isGreaterThanOrEqualTo(1);
    }

    // 8: 取消 outbox
    @Test void shouldWriteOutboxOnCancel() {
        OrderResponse r = orderService.create(USER, newReq());
        long before = outboxMapper.selectCount(null);
        orderService.cancel(USER, r.getOrderId());
        assertThat(outboxMapper.selectCount(null)).isGreaterThan(before);
    }

    // 9: 空 skuItems
    @Test void shouldRejectEmptySkuItems() {
        CreateOrderRequest r = new CreateOrderRequest(); r.setRequestId("test-rid");
        assertThatThrownBy(() -> orderService.create(USER, r))
                .isInstanceOf(NullPointerException.class);
    }

    // 10: 重复消费幂等（重复回调抛异常但不影响状态）
    @Test void shouldHandleDuplicatePayCallback() {
        OrderResponse r = orderService.create(USER, newReq());
        orderService.payCallback(r.getOrderId());
        try { orderService.payCallback(r.getOrderId()); } catch (BizException ignored) {}
        assertThat(orderService.getByOrderId(r.getOrderId()).getOrderStatus()).isEqualTo("PAID");
    }
}