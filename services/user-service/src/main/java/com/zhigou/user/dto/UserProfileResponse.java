package com.zhigou.user.dto;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserProfileResponse {

    @JsonSerialize(using = ToStringSerializer.class)
    private Long userId;
    private String phone;
    private String nickname;
    private String avatarUrl;
    private Integer gender;
    private LocalDate birthday;
    private Integer level;
    private Integer point;
}