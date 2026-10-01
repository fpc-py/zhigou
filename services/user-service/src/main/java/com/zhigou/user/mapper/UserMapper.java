package com.zhigou.user.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zhigou.user.entity.User;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface UserMapper extends BaseMapper<User> {
}