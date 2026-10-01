package com.zhigou.user.dto;

import lombok.Data;

import java.time.LocalDate;

@Data
public class UserProfileRequest {

    private String nickname;
    private String avatarUrl;
    private Integer gender;
    private LocalDate birthday;
}