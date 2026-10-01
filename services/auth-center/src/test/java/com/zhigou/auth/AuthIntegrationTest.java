package com.zhigou.auth;

import com.zhigou.auth.dto.LoginResponse;
import com.zhigou.auth.dto.SendSmsRequest;
import com.zhigou.common.Result;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Testcontainers
class AuthIntegrationTest {

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
    private StringRedisTemplate redisTemplate;

    private String baseUrl() {
        return "http://localhost:" + port;
    }

    // ==================== 测试 1：发码成功 ====================

    @Test
    void shouldSendSmsCodeSuccess() {
        SendSmsRequest req = new SendSmsRequest();
        req.setPhone("13800138001");

        ResponseEntity<Result<Void>> resp = restTemplate.exchange(
                baseUrl() + "/auth/send-sms-code",
                HttpMethod.POST,
                new HttpEntity<>(req),
                new ParameterizedTypeReference<>() {}
        );

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resp.getBody()).isNotNull();
        assertThat(resp.getBody().getCode()).isEqualTo(200);

        // 验证码应存入 Redis
        String cachedCode = redisTemplate.opsForValue().get("auth:sms:13800138001");
        assertThat(cachedCode).isNotNull();
        assertThat(cachedCode).hasSize(6);
    }

    // ==================== 测试 2：错码登录失败 ====================

    @Test
    void shouldFailLoginWithWrongCode() {
        // 先发码
        SendSmsRequest smsReq = new SendSmsRequest();
        smsReq.setPhone("13800138002");
        restTemplate.postForEntity(baseUrl() + "/auth/send-sms-code", smsReq, Result.class);

        // 用错误验证码登录
        Map<String, String> loginReq = Map.of("phone", "13800138002", "code", "000000");
        ResponseEntity<Result<LoginResponse>> resp = restTemplate.exchange(
                baseUrl() + "/auth/login",
                HttpMethod.POST,
                new HttpEntity<>(loginReq),
                new ParameterizedTypeReference<>() {}
        );

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resp.getBody()).isNotNull();
        assertThat(resp.getBody().getCode()).isEqualTo(4002);
    }

    // ==================== 测试 3：正确登录 + Token 可解析 ====================

    @Test
    void shouldLoginAndParseToken() {
        // 发码
        SendSmsRequest smsReq = new SendSmsRequest();
        smsReq.setPhone("13800138003");
        ResponseEntity<Result<Void>> smsResp = restTemplate.exchange(
                baseUrl() + "/auth/send-sms-code",
                HttpMethod.POST,
                new HttpEntity<>(smsReq),
                new ParameterizedTypeReference<>() {}
        );
        assertThat(smsResp.getBody().getCode()).isEqualTo(200);

        // 从 Redis 取真实验证码
        String realCode = redisTemplate.opsForValue().get("auth:sms:13800138003");
        assertThat(realCode).isNotNull();

        // 登录
        Map<String, String> loginReq = Map.of("phone", "13800138003", "code", realCode);
        ResponseEntity<Result<LoginResponse>> loginResp = restTemplate.exchange(
                baseUrl() + "/auth/login",
                HttpMethod.POST,
                new HttpEntity<>(loginReq),
                new ParameterizedTypeReference<>() {}
        );

        assertThat(loginResp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(loginResp.getBody()).isNotNull();
        assertThat(loginResp.getBody().getCode()).isEqualTo(200);

        LoginResponse data = loginResp.getBody().getData();
        assertThat(data).isNotNull();
        assertThat(data.getAccessToken()).isNotBlank();
        assertThat(data.getRefreshToken()).isNotBlank();
        assertThat(data.getExpiresIn()).isGreaterThan(0);

        // Token 是合法的 JWT（三段 base64）
        assertThat(data.getAccessToken()).matches("^[A-Za-z0-9\\-_]+\\.[A-Za-z0-9\\-_]+\\.[A-Za-z0-9\\-_]+$");
    }
}