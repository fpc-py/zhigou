package com.zhigou.product.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zhigou.product.entity.ReviewReply;
import org.apache.ibatis.annotations.Mapper;

/** 商家评论回复 Mapper（无 @MapperScan 服务必须显式 @Mapper）。 */
@Mapper
public interface ReviewReplyMapper extends BaseMapper<ReviewReply> {
}
