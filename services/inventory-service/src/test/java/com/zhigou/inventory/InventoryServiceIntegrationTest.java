package com.zhigou.inventory;

import com.zhigou.common.BizException;
import com.zhigou.inventory.service.InventoryService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles("test")
@Testcontainers
class InventoryServiceIntegrationTest {

    @Container static MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.0").withDatabaseName("zhigou").withUsername("test").withPassword("test");
    @Container static GenericContainer<?> redis = new GenericContainer<>("redis:7-alpine").withExposedPorts(6379);
    @DynamicPropertySource static void cfg(DynamicPropertyRegistry r) {
        r.add("TEST_JDBC_URL", mysql::getJdbcUrl);
        r.add("spring.data.redis.host", redis::getHost);
        r.add("spring.data.redis.port", () -> redis.getMappedPort(6379));
    }

    @Autowired private InventoryService inventoryService;
    @Autowired private StringRedisTemplate redisTemplate;

    @BeforeEach void setUp() { redisTemplate.delete("stock:1"); inventoryService.warmUp(); }

    // 1: 超卖防护
    @Test void shouldRejectOverSell() {
        // skuId=3 库存=1
        assertThat(inventoryService.preDeduct(3L, 1)).isTrue();  // 扣 1 成功
        assertThatThrownBy(() -> inventoryService.preDeduct(3L, 1))
                .isInstanceOf(BizException.class).extracting("code").isEqualTo(40010); // 再扣 1 失败
    }

    // 2: 回滚
    @Test void shouldRollbackStock() {
        String v1 = redisTemplate.opsForValue().get("stock:2");
        inventoryService.preDeduct(2L, 5);
        String v2 = redisTemplate.opsForValue().get("stock:2");
        inventoryService.rollback(2L, 5);
        String v3 = redisTemplate.opsForValue().get("stock:2");
        assertThat(Integer.parseInt(v2)).isEqualTo(Integer.parseInt(v1) - 5);
        assertThat(Integer.parseInt(v3)).isEqualTo(Integer.parseInt(v1));
    }

    // 3: 预热
    @Test void shouldWarmUpFromDb() {
        redisTemplate.delete("stock:1");
        assertThat(redisTemplate.opsForValue().get("stock:1")).isNull();
        inventoryService.warmUp();
        assertThat(redisTemplate.opsForValue().get("stock:1")).isNotNull();
    }

    // 4: 并发预扣不会超卖
    @Test void shouldNotOverSellUnderConcurrency() throws InterruptedException {
        int threads = 20;
        CountDownLatch latch = new CountDownLatch(threads);
        AtomicInteger success = new AtomicInteger(0);
        for (int i = 0; i < threads; i++) {
            new Thread(() -> {
                try { inventoryService.preDeduct(2L, 1); success.incrementAndGet(); }
                catch (BizException ignored) {}
                finally { latch.countDown(); }
            }).start();
        }
        latch.await();
        // skuId=2 库存=10，20 并发各扣 1，成功数应 ≤ 10
        assertThat(success.get()).isLessThanOrEqualTo(10);
    }
}