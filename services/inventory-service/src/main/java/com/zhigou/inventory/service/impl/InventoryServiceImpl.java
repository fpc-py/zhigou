package com.zhigou.inventory.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.zhigou.common.BizException;
import com.zhigou.inventory.entity.Stock;
import com.zhigou.inventory.mapper.StockMapper;
import com.zhigou.inventory.service.InventoryService;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.List;

@Slf4j @Service @RequiredArgsConstructor
public class InventoryServiceImpl implements InventoryService {

    private final StringRedisTemplate redis;
    private final StockMapper stockMapper;
    private final DefaultRedisScript<Long> deductScript = new DefaultRedisScript<>();

    private static final String KEY_PREFIX = "stock:";

    {
        deductScript.setLocation(new ClassPathResource("lua/deduct.lua"));
        deductScript.setResultType(Long.class);
    }

    @Override
    public boolean preDeduct(Long skuId, int count) {
        String key = KEY_PREFIX + skuId;
        Long result = redis.execute(deductScript, Collections.singletonList(key), String.valueOf(count));
        if (result == -1L) {
            log.warn("库存不足: skuId={}, count={}", skuId, count);
            throw new BizException(40010, "库存不足");
        }
        log.info("预扣成功: skuId={}, count={}, remaining={}", skuId, count, result);
        return true;
    }

    @Override @Transactional
    public void confirm(Long skuId, int count) {
        LambdaUpdateWrapper<Stock> wrapper = new LambdaUpdateWrapper<>();
        wrapper.eq(Stock::getSkuId, skuId).ge(Stock::getAvailable, count)
                .setSql("available = available - " + count);
        int rows = stockMapper.update(null, wrapper);
        if (rows == 0) {
            log.error("确认扣减失败（DB兜底）: skuId={}, count={}", skuId, count);
            throw new BizException(500, "库存扣减失败");
        }
        redis.delete(KEY_PREFIX + skuId);
        log.info("确认扣减: skuId={}, count={}", skuId, count);
    }

    @Override
    public void rollback(Long skuId, int count) {
        redis.opsForValue().increment(KEY_PREFIX + skuId, count);
        log.info("库存回滚: skuId={}, count={}", skuId, count);
    }

    @Override @PostConstruct
    public void warmUp() {
        log.info("开始预热库存到 Redis...");
        List<Stock> stocks = stockMapper.selectList(null);
        for (Stock s : stocks) {
            redis.opsForValue().set(KEY_PREFIX + s.getSkuId(), String.valueOf(s.getAvailable()));
        }
        log.info("预热完成: {} 条记录", stocks.size());
    }

    @Override
    public Stock query(Long skuId) {
        if (skuId == null) return null;
        return stockMapper.selectOne(new LambdaQueryWrapper<Stock>().eq(Stock::getSkuId, skuId));
    }
}