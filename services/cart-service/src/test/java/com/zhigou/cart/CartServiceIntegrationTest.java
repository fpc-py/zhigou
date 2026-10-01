package com.zhigou.cart;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhigou.cart.dto.CartAddRequest;
import com.zhigou.cart.dto.CartItemResponse;
import com.zhigou.cart.dto.CartUpdateRequest;
import com.zhigou.cart.service.CartService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Testcontainers
class CartServiceIntegrationTest {

    @Container
    static GenericContainer<?> redisContainer = new GenericContainer<>("redis:7-alpine").withExposedPorts(6379);

    @DynamicPropertySource
    static void configure(DynamicPropertyRegistry r) {
        r.add("spring.data.redis.host", redisContainer::getHost);
        r.add("spring.data.redis.port", () -> redisContainer.getMappedPort(6379));
    }

    @Autowired private CartService cartService;
    @Autowired private StringRedisTemplate redisTemplate;
    @Autowired private RestTemplate restTemplate;

    private MockRestServiceServer mockServer;
    private static final Long USER = 10001L;

    @BeforeEach
    void setUp() {
        redisTemplate.delete("cart:" + USER);
        mockServer = MockRestServiceServer.createServer(restTemplate);
    }

    private void mockProductOk(Long skuId) {
        String json = "{\"code\":200,\"message\":\"success\",\"data\":{\"spuId\":1,\"status\":1}}";
        mockServer.expect(requestTo("http://localhost:9999/product/" + skuId))
                .andRespond(withSuccess(json, org.springframework.http.MediaType.APPLICATION_JSON));
    }

    // ===== 测试 1：加购成功 =====

    @Test
    void shouldAddItemToCart() {
        mockProductOk(100L);

        CartAddRequest req = new CartAddRequest(); req.setSkuId(100L); req.setCount(2);
        cartService.add(USER, req);

        var items = cartService.mine(USER);
        assertThat(items).hasSize(1);
        assertThat(items.get(0).getSkuId()).isEqualTo(100L);
        assertThat(items.get(0).getCount()).isEqualTo(2);
        assertThat(items.get(0).getSelected()).isTrue();
    }

    // ===== 测试 2：更新数量 =====

    @Test
    void shouldUpdateCount() {
        mockProductOk(200L);

        // 先加购
        CartAddRequest addReq = new CartAddRequest(); addReq.setSkuId(200L); addReq.setCount(1);
        cartService.add(USER, addReq);

        // 更新数量
        CartUpdateRequest upReq = new CartUpdateRequest(); upReq.setSkuId(200L); upReq.setCount(5);
        cartService.update(USER, upReq);

        var items = cartService.mine(USER);
        assertThat(items.get(0).getCount()).isEqualTo(5);
    }

    // ===== 测试 3：取购物车 =====

    @Test
    void shouldReturnAllItems() {
        mockProductOk(300L);
        mockProductOk(400L);

        CartAddRequest r1 = new CartAddRequest(); r1.setSkuId(300L); r1.setCount(1);
        cartService.add(USER, r1);
        CartAddRequest r2 = new CartAddRequest(); r2.setSkuId(400L); r2.setCount(3);
        cartService.add(USER, r2);

        var items = cartService.mine(USER);
        assertThat(items).hasSize(2);
    }

    // ===== 测试 4：清空选中 =====

    @Test
    void shouldClearSelectedItems() {
        mockProductOk(500L);
        mockProductOk(600L);

        CartAddRequest r1 = new CartAddRequest(); r1.setSkuId(500L); r1.setCount(1);
        cartService.add(USER, r1);
        CartAddRequest r2 = new CartAddRequest(); r2.setSkuId(600L); r2.setCount(2);
        cartService.add(USER, r2);

        // 取消选中 600
        CartUpdateRequest upReq = new CartUpdateRequest(); upReq.setSkuId(600L); upReq.setSelected(false);
        cartService.update(USER, upReq);

        // 清空选中（只删 500）
        cartService.clearSelected(USER);

        var items = cartService.mine(USER);
        assertThat(items).hasSize(1);
        assertThat(items.get(0).getSkuId()).isEqualTo(600L);
    }
}