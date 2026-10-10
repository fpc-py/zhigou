package com.zhigou.user.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zhigou.user.entity.UserFavorite;
import org.apache.ibatis.annotations.Mapper;

/**
 * 用户收藏 Mapper（无 @MapperScan，必须显式 @Mapper）
 */
@Mapper
public interface UserFavoriteMapper extends BaseMapper<UserFavorite> {
}
