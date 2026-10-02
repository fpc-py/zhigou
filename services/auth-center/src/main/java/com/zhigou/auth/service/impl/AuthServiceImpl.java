package com.zhigou.auth.service.impl;

import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.RandomUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zhigou.auth.dto.LoginResponse;
import com.zhigou.auth.entity.User;
import com.zhigou.auth.mapper.UserMapper;
import com.zhigou.auth.service.AuthService;
import com.zhigou.auth.util.JwtUtil;
import com.zhigou.common.BizException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private final StringRedisTemplate redisTemplate;
    private final UserMapper userMapper;
    private final JwtUtil jwtUtil;

    private static final String SMS_CODE_PREFIX = "auth:sms:";
    private static final long SMS_CODE_TTL = 5; // 5 分钟

    @Override
    public void sendSmsCode(String phone) {
        String code = RandomUtil.randomNumbers(6);
        String key = SMS_CODE_PREFIX + phone;
        redisTemplate.opsForValue().set(key, code, SMS_CODE_TTL, TimeUnit.MINUTES);

        // 本地开发模式：验证码打到日志
        log.info("===== 验证码 [phone={}] -> [{}] =====", phone, code);
    }

    @Override
    public LoginResponse login(String phone, String code) {
        // 1. 校验验证码
        String key = SMS_CODE_PREFIX + phone;
        String cachedCode = redisTemplate.opsForValue().get(key);
        if (cachedCode == null) {
            throw new BizException(4001, "验证码已过期，请重新获取");
        }
        if (!cachedCode.equals(code)) {
            throw new BizException(4002, "验证码错误");
        }

        // 2. 校验通过，销毁验证码
        redisTemplate.delete(key);

        // 3. 查找或创建用户
        User user = userMapper.selectOne(
                new LambdaQueryWrapper<User>().eq(User::getPhone, phone)
        );
        if (user == null) {
            user = new User();
            user.setUserId(IdUtil.getSnowflakeNextId());
            user.setPhone(phone);
            userMapper.insert(user);
            log.info("新用户注册: userId={}, phone={}", user.getUserId(), phone);
        }

        // 4. 签发 token
        String accessToken = jwtUtil.createAccessToken(user.getId(), phone);
        String refreshToken = jwtUtil.createRefreshToken(user.getId(), phone);

        log.info("登录成功: userId={}", user.getId());

        return LoginResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .expiresIn(7200)
                .build();
    }
}