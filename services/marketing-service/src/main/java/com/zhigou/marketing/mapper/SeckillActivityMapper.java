package com.zhigou.marketing.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zhigou.marketing.entity.SeckillActivity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface SeckillActivityMapper extends BaseMapper<SeckillActivity> {

    /**
     * 原子预扣库存（防超卖）：
     * 单条 UPDATE 条件含「已售 + 购买数 <= 总量」+「活动时间内」，MySQL 行锁保证并发下不会超卖。
     * @return 受影响行数：1=扣减成功 0=超卖/未开始/已结束
     */
    @Update("UPDATE seckill_activity SET sold = sold + #{count} " +
            "WHERE id = #{id} AND deleted = 0 AND status = 1 " +
            "AND sold + #{count} <= total " +
            "AND start_time <= NOW(3) AND end_time >= NOW(3)")
    int tryDeduct(@Param("id") Long id, @Param("count") Integer count);

    /** 释放预扣（购买失败回滚已售） */
    @Update("UPDATE seckill_activity SET sold = sold - #{count} " +
            "WHERE id = #{id} AND deleted = 0 AND sold >= #{count}")
    int release(@Param("id") Long id, @Param("count") Integer count);
}
