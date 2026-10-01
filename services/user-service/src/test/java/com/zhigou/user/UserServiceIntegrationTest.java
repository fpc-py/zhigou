package com.zhigou.user;

import com.zhigou.user.dto.*;
import com.zhigou.user.entity.Address;
import com.zhigou.user.entity.User;
import com.zhigou.user.mapper.AddressMapper;
import com.zhigou.user.mapper.UserMapper;
import com.zhigou.common.Result;
import io.jsonwebtoken.Jwts;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Testcontainers
class UserServiceIntegrationTest {

    private static final String JWT_SECRET = "test-jwt-secret-for-user-service!";
    private static final AtomicLong userIdCounter = new AtomicLong(20001);

    @Container
    static MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.0")
            .withDatabaseName("zhigou")
            .withUsername("test")
            .withPassword("test");

    @Container
    static GenericContainer<?> redis = new GenericContainer<>("redis:7-alpine")
            .withExposedPorts(6379);

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("TEST_JDBC_URL", mysql::getJdbcUrl);
        registry.add("spring.data.redis.host", redis::getHost);
        registry.add("spring.data.redis.port", () -> redis.getMappedPort(6379));
    }

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private AddressMapper addressMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private long nextUserId() {
        return userIdCounter.incrementAndGet();
    }

    private String baseUrl() {
        return "http://localhost:" + port;
    }

    private HttpHeaders authHeaders(long userId) {
        String token = createJwt(userId);
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        return headers;
    }

    private String createJwt(Long userId) {
        var key = new SecretKeySpec(JWT_SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
        return Jwts.builder()
                .subject(String.valueOf(userId))
                .claim("phone", "13800138000")
                .signWith(key)
                .compact();
    }

    @BeforeEach
    void setUp() {
        // 物理删除，绕过 @TableLogic
        jdbcTemplate.execute("DELETE FROM address");
        jdbcTemplate.execute("DELETE FROM user");
    }

    // ==================== 测试 1：自动建档 ====================

    @Test
    void shouldRegisterNewUserAndReturnSameIdOnRetry() {
        long userId = nextUserId();
        HttpHeaders headers = authHeaders(userId);
        RegisterRequest req = new RegisterRequest();
        req.setPhone("13800138001");

        // 首次建档
        ResponseEntity<Result<java.util.Map>> resp1 = restTemplate.exchange(
                baseUrl() + "/user/register-if-absent",
                HttpMethod.POST,
                new HttpEntity<>(req, headers),
                new ParameterizedTypeReference<>() {}
        );
        assertThat(resp1.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resp1.getBody().getCode()).isEqualTo(200);
        Long userId1 = ((Number) resp1.getBody().getData().get("user_id")).longValue();
        assertThat(userId1).isNotNull();

        // 二次同 phone → 返回同一 userId
        ResponseEntity<Result<java.util.Map>> resp2 = restTemplate.exchange(
                baseUrl() + "/user/register-if-absent",
                HttpMethod.POST,
                new HttpEntity<>(req, headers),
                new ParameterizedTypeReference<>() {}
        );
        assertThat(resp2.getStatusCode()).isEqualTo(HttpStatus.OK);
        Long userId2 = ((Number) resp2.getBody().getData().get("user_id")).longValue();
        assertThat(userId2).isEqualTo(userId1);

        // 数据库中 phone 是密文
        User dbUser = userMapper.selectOne(new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<User>()
                .eq(User::getUserId, userId1));
        assertThat(dbUser.getPhone()).isNotEqualTo("13800138001");
    }

    // ==================== 测试 2：修改资料 ====================

    @Test
    void shouldUpdateProfile() {
        long userId = nextUserId();
        HttpHeaders headers = authHeaders(userId);
        RegisterRequest regReq = new RegisterRequest();
        regReq.setPhone("13800138002");
        restTemplate.postForEntity(baseUrl() + "/user/register-if-absent",
                new HttpEntity<>(regReq, headers), Result.class);

        // 修改昵称
        UserProfileRequest updateReq = new UserProfileRequest();
        updateReq.setNickname("测试昵称");
        updateReq.setGender(1);

        ResponseEntity<Result<UserProfileResponse>> resp = restTemplate.exchange(
                baseUrl() + "/user/profile",
                HttpMethod.PUT,
                new HttpEntity<>(updateReq, headers),
                new ParameterizedTypeReference<>() {}
        );

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resp.getBody().getCode()).isEqualTo(200);
        assertThat(resp.getBody().getData().getNickname()).isEqualTo("测试昵称");
        assertThat(resp.getBody().getData().getGender()).isEqualTo(1);
    }

    // ==================== 测试 3：越权访问他人地址 ====================

    @Test
    void shouldRejectAccessToOthersAddress() {
        long userA = nextUserId();
        long userB = nextUserId();
        HttpHeaders headersA = authHeaders(userA);
        registerAndAddAddress(headersA, userA, "13800138003");

        // 获取地址 ID
        ResponseEntity<Result<List<AddressResponse>>> listResp = restTemplate.exchange(
                baseUrl() + "/address/list",
                HttpMethod.GET,
                new HttpEntity<>(headersA),
                new ParameterizedTypeReference<>() {}
        );
        Long addressId = listResp.getBody().getData().get(0).getAddressId();

        // 用户 B 尝试修改用户 A 的地址
        HttpHeaders headersB = authHeaders(userB);
        AddressRequest updateReq = new AddressRequest();
        updateReq.setReceiverName("hacker");
        updateReq.setReceiverPhone("13900000001");
        updateReq.setProvince("北京");
        updateReq.setCity("北京");
        updateReq.setDistrict("朝阳");
        updateReq.setDetail("某地");

        ResponseEntity<Result<AddressResponse>> resp = restTemplate.exchange(
                baseUrl() + "/address/" + addressId,
                HttpMethod.PUT,
                new HttpEntity<>(updateReq, headersB),
                new ParameterizedTypeReference<>() {}
        );

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resp.getBody().getCode()).isEqualTo(403);
    }

    // ==================== 测试 4：设默认地址时旧默认清除 ====================

    @Test
    void shouldClearOldDefaultWhenSetNew() {
        long userId = nextUserId();
        HttpHeaders headers = authHeaders(userId);

        // 添加两个地址
        AddressRequest req1 = buildAddressReq("张三", "广东", "深圳", "南山");
        AddressRequest req2 = buildAddressReq("李四", "广东", "广州", "天河");

        restTemplate.postForEntity(baseUrl() + "/address",
                new HttpEntity<>(req1, headers), Result.class);
        restTemplate.postForEntity(baseUrl() + "/address",
                new HttpEntity<>(req2, headers), Result.class);

        // 获取地址列表
        ResponseEntity<Result<List<AddressResponse>>> listResp = restTemplate.exchange(
                baseUrl() + "/address/list",
                HttpMethod.GET,
                new HttpEntity<>(headers),
                new ParameterizedTypeReference<>() {}
        );
        List<AddressResponse> list = listResp.getBody().getData();

        // 第一个是默认
        assertThat(list.get(0).getIsDefault()).isEqualTo(1);
        assertThat(list.get(1).getIsDefault()).isEqualTo(0);

        // 设第二个为默认
        restTemplate.exchange(
                baseUrl() + "/address/" + list.get(1).getAddressId() + "/default",
                HttpMethod.PUT,
                new HttpEntity<>(headers),
                new ParameterizedTypeReference<Result<Void>>() {}
        );

        // 重新查询，验证旧默认清除
        ResponseEntity<Result<List<AddressResponse>>> listResp2 = restTemplate.exchange(
                baseUrl() + "/address/list",
                HttpMethod.GET,
                new HttpEntity<>(headers),
                new ParameterizedTypeReference<>() {}
        );
        List<AddressResponse> list2 = listResp2.getBody().getData();
        assertThat(list2.get(0).getIsDefault()).isEqualTo(0);
        assertThat(list2.get(1).getIsDefault()).isEqualTo(1);
    }

    // ==================== 测试 5：requestId 幂等 ====================

    @Test
    void shouldDeduplicateByRequestId() {
        long userId = nextUserId();
        HttpHeaders headers = authHeaders(userId);
        String requestId = UUID.randomUUID().toString().replace("-", "");
        headers.set("X-Request-Id", requestId);

        RegisterRequest req = new RegisterRequest();
        req.setPhone("13800138005");

        // 第一次请求
        ResponseEntity<Result<java.util.Map>> resp1 = restTemplate.exchange(
                baseUrl() + "/user/register-if-absent",
                HttpMethod.POST,
                new HttpEntity<>(req, headers),
                new ParameterizedTypeReference<>() {}
        );
        assertThat(resp1.getStatusCode()).isEqualTo(HttpStatus.OK);

        // 第二次同 requestId 请求（应该被幂等拦截）
        ResponseEntity<Result<java.util.Map>> resp2 = restTemplate.exchange(
                baseUrl() + "/user/register-if-absent",
                HttpMethod.POST,
                new HttpEntity<>(req, headers),
                new ParameterizedTypeReference<>() {}
        );
        assertThat(resp2.getStatusCode()).isEqualTo(HttpStatus.OK);

        // 数据库只有一条记录
        Long count = userMapper.selectCount(null);
        assertThat(count).isEqualTo(1);
    }

    // ==================== 测试 6：手机号加密存储 ====================

    @Test
    void shouldEncryptPhoneAndDecryptOnRead() {
        long userId = nextUserId();
        HttpHeaders headers = authHeaders(userId);
        RegisterRequest regReq = new RegisterRequest();
        regReq.setPhone("13800138006");

        // 建档
        ResponseEntity<Result<java.util.Map>> regResp = restTemplate.exchange(
                baseUrl() + "/user/register-if-absent",
                HttpMethod.POST,
                new HttpEntity<>(regReq, headers),
                new ParameterizedTypeReference<>() {}
        );
        Long registeredUserId = ((Number) regResp.getBody().getData().get("user_id")).longValue();

        // 查库：phone 是密文
        User dbUser = userMapper.selectOne(new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<User>()
                .eq(User::getUserId, registeredUserId));
        assertThat(dbUser.getPhone()).isNotNull();
        assertThat(dbUser.getPhone()).isNotEqualTo("13800138006");

        // 查接口：返回明文
        ResponseEntity<Result<UserProfileResponse>> profileResp = restTemplate.exchange(
                baseUrl() + "/user/profile",
                HttpMethod.GET,
                new HttpEntity<>(headers),
                new ParameterizedTypeReference<>() {}
        );
        assertThat(profileResp.getBody().getData().getPhone()).isEqualTo("13800138006");
    }

    // ==================== Helper ====================

    private void registerAndAddAddress(HttpHeaders headers, long userId, String phone) {
        RegisterRequest regReq = new RegisterRequest();
        regReq.setPhone(phone);
        restTemplate.postForEntity(baseUrl() + "/user/register-if-absent",
                new HttpEntity<>(regReq, headers), Result.class);

        AddressRequest addrReq = buildAddressReq("测试用户", "北京", "北京", "朝阳");
        restTemplate.postForEntity(baseUrl() + "/address",
                new HttpEntity<>(addrReq, headers), Result.class);
    }

    private AddressRequest buildAddressReq(String name, String province, String city, String district) {
        AddressRequest req = new AddressRequest();
        req.setReceiverName(name);
        req.setReceiverPhone("13800000001");
        req.setProvince(province);
        req.setCity(city);
        req.setDistrict(district);
        req.setDetail("某某路1号");
        return req;
    }
}