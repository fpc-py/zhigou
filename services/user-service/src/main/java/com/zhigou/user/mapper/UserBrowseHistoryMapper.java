package com.zhigou.user.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zhigou.user.entity.UserBrowseHistory;
import org.apache.ibatis.annotations.Mapper;

/**
 * 用户浏览历史 Mapper（无 @MapperScan，必须显式 @Mapper）
 */
@Mapper
public interface UserBrowseHistoryMapper extends BaseMapper<UserBrowseHistory> {
}
