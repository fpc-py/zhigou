package com.zhigou.cart.service.impl;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhigou.cart.dto.CartAddRequest;
import com.zhigou.cart.dto.CartItemResponse;
import com.zhigou.cart.dto.CartUpdateRequest;
import com.zhigou.cart.service.CartService;
import com.zhigou.common.BizException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Slf4j @Service @RequiredArgsConstructor
public class CartServiceImpl implements CartService {

    private final StringRedisTemplate redis;
    private final ObjectMapper mapper;
    private final RestTemplate restTemplate;

    @Value("${product-service.url}") private String productServiceUrl;

    private static final String KEY_PREFIX = "cart:";

    @Override
    public void add(Long userId, CartAddRequest req) {
        // 1. 调 product-service 校验
        validateSku(req.getSkuId());

        // 2. 写入 Redis Hash
        CartItemResponse item = CartItemResponse.builder()
                .skuId(req.getSkuId()).count(req.getCount()).selected(true).priceAtAdd(0L).build();
        try {
            redis.opsForHash().put(KEY_PREFIX + userId, String.valueOf(req.getSkuId()), mapper.writeValueAsString(item));
        } catch (Exception e) {
            throw new BizException(500, "购物车操作失败");
        }
        log.info("加购: userId={}, skuId={}, count={}", userId, req.getSkuId(), req.getCount());
    }

    @Override
    public void update(Long userId, CartUpdateRequest req) {
        String key = KEY_PREFIX + userId;
        String field = String.valueOf(req.getSkuId());
        String raw = (String) redis.opsForHash().get(key, field);
        if (raw == null) throw new BizException(404, "购物车无该商品");

        try {
            CartItemResponse item = mapper.readValue(raw, CartItemResponse.class);
            if (req.getCount() != null) item.setCount(req.getCount());
            if (req.getSelected() != null) item.setSelected(req.getSelected());
            redis.opsForHash().put(key, field, mapper.writeValueAsString(item));
        } catch (Exception e) {
            throw new BizException(500, "更新失败");
        }
    }

    @Override
    public List<CartItemResponse> mine(Long userId) {
        Map<Object, Object> entries = redis.opsForHash().entries(KEY_PREFIX + userId);
        List<CartItemResponse> list = new ArrayList<>();
        for (Object v : entries.values()) {
            try { list.add(mapper.readValue((String) v, CartItemResponse.class)); } catch (Exception ignored) {}
        }
        return list;
    }

    @Override
    public void clearSelected(Long userId) {
        String key = KEY_PREFIX + userId;
        Map<Object, Object> entries = redis.opsForHash().entries(key);
        for (Object v : entries.values()) {
            try {
                CartItemResponse item = mapper.readValue((String) v, CartItemResponse.class);
                if (Boolean.TRUE.equals(item.getSelected())) {
                    redis.opsForHash().delete(key, String.valueOf(item.getSkuId()));
                }
            } catch (Exception ignored) {}
        }
        log.info("清空选中: userId={}", userId);
    }

    private void validateSku(Long skuId) {
        try {
            ResponseEntity<String> resp = restTemplate.getForEntity(
                    productServiceUrl + "/product/" + skuId, String.class);
            if (!resp.getStatusCode().is2xxSuccessful()) {
                throw new BizException(400, "商品不存在或已下架");
            }
        } catch (BizException e) { throw e; }
        catch (Exception e) {
            log.warn("调用 product-service 失败: skuId={}, err={}", skuId, e.getMessage());
            throw new BizException(500, "商品校验服务不可用，请稍后重试");
        }
    }
}