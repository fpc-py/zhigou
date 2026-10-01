package com.zhigou.user.service;

import com.zhigou.user.dto.UserProfileRequest;
import com.zhigou.user.dto.UserProfileResponse;

public interface UserService {

    /**
     * 自动建档：phone 不存在则新建，返回 userId。幂等。
     */
    Long registerIfAbsent(Long userId, String phone);

    /**
     * 查询当前用户资料
     */
    UserProfileResponse getProfile(Long userId);

    /**
     * 修改资料
     */
    UserProfileResponse updateProfile(Long userId, UserProfileRequest request);
}