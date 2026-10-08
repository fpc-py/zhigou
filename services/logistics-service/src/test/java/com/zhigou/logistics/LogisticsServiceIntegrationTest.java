package com.zhigou.logistics;

import com.zhigou.logistics.entity.Shipment;
import com.zhigou.logistics.mapper.ShipmentMapper;
import com.zhigou.logistics.service.impl.LogisticsServiceImpl;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test") @Testcontainers
@Import(TestSecurityConfig.class)
class LogisticsServiceIntegrationTest {

    @Container static MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.0").withDatabaseName("zhigou").withUsername("test").withPassword("test").withStartupTimeout(Duration.ofSeconds(120));
    @Container static GenericContainer<?> redis = new GenericContainer<>("redis:7-alpine").withExposedPorts(6379).withStartupTimeout(Duration.ofSeconds(120)).waitingFor(Wait.forListeningPort());
    @DynamicPropertySource static void cfg(DynamicPropertyRegistry r) {
        r.add("TEST_JDBC_URL", mysql::getJdbcUrl);
        r.add("spring.datasource.url", mysql::getJdbcUrl);
        r.add("spring.datasource.username", mysql::getUsername);
        r.add("spring.datasource.password", mysql::getPassword);
        r.add("spring.data.redis.host", redis::getHost);
        r.add("spring.data.redis.port", () -> redis.getMappedPort(6379));
    }

    @Autowired private LogisticsServiceImpl service;
    @Autowired private ShipmentMapper shipmentMapper;

    // 1: 首重内运费
    @Test void shouldCalculateFirstWeightFee() {
        assertThat(service.calculate(5000L, 800)).isEqualTo(1000L);
    }

    // 2: 续重运费
    @Test void shouldCalculateContinuedWeightFee() {
        // 1000g首重10元 + 续重1500g(2*1000→2*2元=4元) = 14元 = 1400分
        assertThat(service.calculate(5000L, 2500)).isEqualTo(1400L);
    }

    // 3: 满额包邮
    @Test void shouldBeFreeShippingAboveThreshold() {
        assertThat(service.calculate(10000L, 5000)).isEqualTo(0L);
    }

    // 4: 建运单+重复幂等
    @Test void shouldCreateShipmentAndBeIdempotent() {
        String sn1 = service.createShipment("ORD-001", 10001L, "{}", 1000, 1000L);
        String sn2 = service.createShipment("ORD-001", 10001L, "{}", 1000, 1000L);
        assertThat(sn1).isEqualTo(sn2);
        assertThat(sn1).startsWith("SF");
    }

    // 5: 查轨迹5节点
    @Test void shouldReturnFiveTrackNodes() {
        String sn = service.createShipment("ORD-TRACK", 10001L, "{}", 1000, 1000L);
        assertThat(service.track(sn)).hasSize(5);
    }

    // 6: 取消
    @Test void shouldCancelShipment() {
        String sn = service.createShipment("ORD-CANCEL", 10001L, "{}", 1000, 1000L);
        service.cancel("ORD-CANCEL");
        Shipment s = shipmentMapper.selectOne(new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<Shipment>().eq(Shipment::getOrderNo, "ORD-CANCEL"));
        assertThat(s.getStatus()).isEqualTo("CANCELED");
    }
}