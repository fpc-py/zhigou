package com.zhigou.user.service.impl;

import cn.hutool.core.util.IdUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zhigou.common.BizException;
import com.zhigou.user.dto.UserProfileRequest;
import com.zhigou.user.dto.UserProfileResponse;
import com.zhigou.user.entity.User;
import com.zhigou.user.mapper.UserMapper;
import com.zhigou.user.service.UserService;
import com.zhigou.user.util.PhoneEncryptUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

    private final UserMapper userMapper;

    @Value("${phone.secret}")
    private String phoneSecret;

    @Override
    public Long registerIfAbsent(Long authUserId, String phone) {
        String encryptedPhone = PhoneEncryptUtil.encrypt(phone, phoneSecret);

        User existing = userMapper.selectOne(
                new LambdaQueryWrapper<User>().eq(User::getPhone, encryptedPhone)
        );
        if (existing != null) {
            log.info("用户已存在: userId={}", existing.getUserId());
            return existing.getUserId();
        }

        User user = new User();
        user.setUserId(authUserId);
        user.setPhone(encryptedPhone);
        user.setNickname("用户" + IdUtil.fastSimpleUUID().substring(0, 8));
        user.setLevel(0);
        user.setPoint(0);
        userMapper.insert(user);

        log.info("新用户注册: userId={}, phoneHash={}", user.getUserId(), phone.substring(0, 3) + "****");
        return user.getUserId();
    }

    @Override
    public UserProfileResponse getProfile(Long userId) {
        User user = userMapper.selectOne(
                new LambdaQueryWrapper<User>().eq(User::getUserId, userId)
        );
        if (user == null) {
            throw new BizException(404, "用户不存在");
        }
        return toResponse(user);
    }

    @Override
    public UserProfileResponse updateProfile(Long userId, UserProfileRequest request) {
        User user = userMapper.selectOne(
                new LambdaQueryWrapper<User>().eq(User::getUserId, userId)
        );
        if (user == null) {
            throw new BizException(404, "用户不存在");
        }

        if (request.getNickname() != null) user.setNickname(request.getNickname());
        if (request.getAvatarUrl() != null) user.setAvatarUrl(request.getAvatarUrl());
        if (request.getGender() != null) user.setGender(request.getGender());
        if (request.getBirthday() != null) user.setBirthday(request.getBirthday());

        userMapper.updateById(user);
        log.info("用户资料更新: userId={}", userId);
        return toResponse(user);
    }

    private UserProfileResponse toResponse(User user) {
        String plainPhone = PhoneEncryptUtil.decrypt(user.getPhone(), phoneSecret);
        return UserProfileResponse.builder()
                .userId(user.getUserId())
                .phone(plainPhone)
                .nickname(user.getNickname())
                .avatarUrl(user.getAvatarUrl())
                .gender(user.getGender())
                .birthday(user.getBirthday())
                .level(user.getLevel())
                .point(user.getPoint())
                .build();
    }
}