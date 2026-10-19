package com.zhigou.marketing.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zhigou.common.BizException;
import com.zhigou.marketing.entity.SeckillActivity;
import com.zhigou.marketing.mapper.SeckillActivityMapper;
import com.zhigou.marketing.service.SeckillService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Slf4j @Service @RequiredArgsConstructor
public class SeckillServiceImpl implements SeckillService {

    private final SeckillActivityMapper seckillMapper;

    @Override
    public List<SeckillActivity> listActive() {
        return seckillMapper.selectList(new LambdaQueryWrapper<SeckillActivity>()
                .eq(SeckillActivity::getStatus, 1)
                .le(SeckillActivity::getStartTime, LocalDateTime.now())
                .ge(SeckillActivity::getEndTime, LocalDateTime.now())
                .orderByAsc(SeckillActivity::getEndTime));
    }

    @Override
    public SeckillActivity detail(Long id) {
        SeckillActivity a = seckillMapper.selectById(id);
        if (a == null) throw new BizException(404, "秒杀活动不存在");
        return a;
    }

    /**
     * 秒杀预扣（防超卖核心）：
     * 1) 限购校验（每人累计已购 + 本次 <= perUserLimit，按 userId 聚合已售不可查——用购买记录缺失时，
     *    以「活动+用户」维度的轻量校验：每次 pre-freeze 前校验本次购买数 <= 限购；限购的跨请求统计由
     *    订单侧购买记录兜底，营销侧防单次超购即可，避免为演示引入额外事务表）
     * 2) 原子 UPDATE 防超卖（sold + n <= total 且活动时间窗口内）
     * 3) 返回秒杀价快照，供下单计价
     */
    @Override @Transactional
    public Map<String, Object> buy(Long userId, Long activityId, Integer count) {
        if (count == null || count < 1) throw new BizException(40040, "购买数量不合法");
        SeckillActivity a = detail(activityId);
        if (count > a.getPerUserLimit()) throw new BizException(40041, "超过每人限购 " + a.getPerUserLimit() + " 件");
        if (a.getStatus() != 1) throw new BizException(40042, "秒杀活动未上架");

        int affected = seckillMapper.tryDeduct(activityId, count);
        if (affected == 0) {
            SeckillActivity cur = seckillMapper.selectById(activityId);
            if (cur == null || cur.getStatus() != 1) throw new BizException(40042, "秒杀活动未上架");
            LocalDateTime now = LocalDateTime.now();
            if (now.isBefore(cur.getStartTime())) throw new BizException(40043, "秒杀未开始");
            if (now.isAfter(cur.getEndTime())) throw new BizException(40044, "秒杀已结束");
            throw new BizException(40045, "库存不足，手慢了！剩余 " + Math.max(0, cur.getTotal() - cur.getSold()) + " 件");
        }

        int remain = Math.max(0, a.getTotal() - a.getSold() - count);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("activityId", activityId);
        out.put("skuId", a.getSkuId());
        out.put("seckillPrice", a.getSeckillPrice());
        out.put("originPrice", a.getOriginPrice());
        out.put("boughtCount", count);
        out.put("remainBuyable", remain);
        out.put("expireAt", a.getEndTime());
        log.info("秒杀预扣: userId={}, activityId={}, count={}, 剩余可购={}", userId, activityId, count, remain);
        return out;
    }

    @Override @Transactional
    public void release(Long activityId, Integer count) {
        if (count == null || count < 1) return;
        int affected = seckillMapper.release(activityId, count);
        if (affected == 0) log.warn("秒杀释放失败(已售不足): activityId={}, count={}", activityId, count);
        else log.info("秒杀释放: activityId={}, count={}", activityId, count);
    }
}
