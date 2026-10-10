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

import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.Map;

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

    /**
     * 按订单整体回滚库存（幂等）。
     * 以 Redis SETNX（键 inv:rb:{orderId}，TTL 7 天）保证同一订单只释放一次，
     * 兼容「关单同步调用」与「ORDER_CLOSED MQ 兜底消费」双通道，避免重复加回。
     */
    @Override
    public void rollbackOrder(String orderId, List<Map<String, Object>> items) {
        if (orderId == null || orderId.isBlank() || items == null || items.isEmpty()) {
            log.warn("库存回滚参数缺失: orderId={}, items={}", orderId, items == null ? "null" : items.size());
            return;
        }
        String key = "inv:rb:" + orderId;
        Boolean first = redis.opsForValue().setIfAbsent(key, "1", Duration.ofDays(7));
        if (!Boolean.TRUE.equals(first)) {
            log.info("库存回滚幂等跳过（该订单已释放）: orderId={}", orderId);
            return;
        }
        for (Map<String, Object> item : items) {
            Object skuObj = item.get("skuId");
            Object countObj = item.get("count");
            if (skuObj == null || countObj == null) continue;
            long skuId = ((Number) skuObj).longValue();
            int count = ((Number) countObj).intValue();
            redis.opsForValue().increment(KEY_PREFIX + skuId, count);
            log.info("库存回滚（订单级）: orderId={}, skuId={}, count={}", orderId, skuId, count);
        }
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

    @Override
    public List<Stock> lowStock(int threshold) {
        return stockMapper.selectList(new LambdaQueryWrapper<Stock>()
                .le(Stock::getAvailable, threshold)
                .orderByAsc(Stock::getAvailable));
    }
}