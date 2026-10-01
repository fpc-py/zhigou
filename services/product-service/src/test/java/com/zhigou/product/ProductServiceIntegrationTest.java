package com.zhigou.product;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhigou.product.dto.SpuCreateRequest;
import com.zhigou.product.dto.SpuDetailResponse;
import com.zhigou.product.mapper.ProductSkuMapper;
import com.zhigou.product.mapper.ProductSpuMapper;
import com.zhigou.product.service.ProductService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Testcontainers
class ProductServiceIntegrationTest {

    @Container
    static MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.0")
            .withDatabaseName("zhigou").withUsername("test").withPassword("test");

    @Container
    static GenericContainer<?> redis = new GenericContainer<>("redis:7-alpine")
            .withExposedPorts(6379);

    @DynamicPropertySource
    static void configure(DynamicPropertyRegistry r) {
        r.add("TEST_JDBC_URL", mysql::getJdbcUrl);
        r.add("spring.data.redis.host", redis::getHost);
        r.add("spring.data.redis.port", () -> redis.getMappedPort(6379));
    }

    @Autowired private ProductService productService;
    @Autowired private ProductSpuMapper spuMapper;
    @Autowired private ProductSkuMapper skuMapper;
    @Autowired private StringRedisTemplate redisTemplate;
    @Autowired private JdbcTemplate jdbc;

    @BeforeEach
    void setUp() {
        jdbc.execute("DELETE FROM product_sku");
        jdbc.execute("DELETE FROM product_spu");
        redisTemplate.delete(redisTemplate.keys("product:*"));
    }

    private Long createSpu(String name) {
        SpuCreateRequest req = new SpuCreateRequest();
        req.setCategoryId(1L); req.setBrandId(1L); req.setName(name);
        req.setSubtitle("副标题"); req.setDescription("<p>详情</p>");
        req.setMainImage("https://img.example.com/test.jpg");
        SpuCreateRequest.SkuItem sku = new SpuCreateRequest.SkuItem();
        sku.setSpecName("颜色"); sku.setSpecValue("红色"); sku.setPrice(9900L); sku.setStock(100);
        req.setSkus(List.of(sku));
        return productService.createSpu(req);
    }

    // ===== 测试 1：缓存命中 =====

    @Test
    void shouldReturnCachedDetailOnSecondCall() {
        Long spuId = createSpu("缓存测试商品");

        // 首次查 DB
        SpuDetailResponse r1 = productService.getDetail(spuId);
        assertThat(r1).isNotNull();
        assertThat(r1.getName()).isEqualTo("缓存测试商品");

        // 确认缓存已写入
        String cached = redisTemplate.opsForValue().get("product:detail:" + spuId);
        assertThat(cached).isNotNull();

        // 二次查询（应命中缓存）
        redisTemplate.delete("product:detail:" + spuId); // 先清掉验证写入
        SpuDetailResponse r2 = productService.getDetail(spuId);
        assertThat(r2.getName()).isEqualTo("缓存测试商品");
    }

    // ===== 测试 2：更新后缓存失效 =====

    @Test
    void shouldEvictCacheOnUpdate() {
        Long spuId = createSpu("待更新商品");

        // 首次查，写入缓存
        productService.getDetail(spuId);
        assertThat(redisTemplate.opsForValue().get("product:detail:" + spuId)).isNotNull();

        // 更新
        SpuCreateRequest updateReq = new SpuCreateRequest();
        updateReq.setCategoryId(1L); updateReq.setName("已更新商品名");
        productService.updateSpu(spuId, updateReq);

        // 缓存应已删除
        assertThat(redisTemplate.opsForValue().get("product:detail:" + spuId)).isNull();

        // 再查应走 DB 拿到新值
        SpuDetailResponse r = productService.getDetail(spuId);
        assertThat(r.getName()).isEqualTo("已更新商品名");
    }

    // ===== 测试 3：下架商品详情返回 null =====

    @Test
    void shouldReturnNullForOffShelfProduct() {
        Long spuId = createSpu("待下架商品");
        productService.offShelf(spuId);

        SpuDetailResponse r = productService.getDetail(spuId);
        assertThat(r).isNotNull(); // 仍能查到详情
        assertThat(r.getStatus()).isEqualTo(0); // 状态为下架
    }

    // ===== 测试 4：分页只返上架商品 =====

    @Test
    void shouldPageOnlyListedProducts() {
        createSpu("上架商品A");
        Long spuB = createSpu("上架商品B（后下架）");
        createSpu("上架商品C");

        // 下架 B
        productService.offShelf(spuB);

        var page = productService.page(new com.zhigou.product.dto.SpuPageQuery());
        assertThat(page.getTotal()).isEqualTo(2);
        page.getRecords().forEach(r -> assertThat(r.getStatus()).isEqualTo(1));
    }

    // ===== 测试 5：详情包含 SKU 列表 =====

    @Test
    void shouldReturnSkuListInDetail() {
        Long spuId = createSpu("带SKU商品");

        SpuDetailResponse detail = productService.getDetail(spuId);
        assertThat(detail.getSkus()).isNotNull();
        assertThat(detail.getSkus()).hasSize(1);
        assertThat(detail.getSkus().get(0).getSpecName()).isEqualTo("颜色");
        assertThat(detail.getSkus().get(0).getSpecValue()).isEqualTo("红色");
        assertThat(detail.getSkus().get(0).getPrice()).isEqualTo(9900L);
    }
}