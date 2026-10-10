package com.zhigou.product.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhigou.common.Result;
import com.zhigou.product.dto.SpuCreateRequest;
import com.zhigou.product.dto.SpuDetailResponse;
import com.zhigou.product.dto.SpuPageQuery;
import com.zhigou.product.entity.Brand;
import com.zhigou.product.entity.Category;
import com.zhigou.product.entity.ProductSku;
import com.zhigou.product.service.MerchantForecastService;
import com.zhigou.product.service.ProductService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.bind.annotation.*;

import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * 商品 Controller。
 * P0-D4 压测瓶颈优化：/product/page（最高频读接口）加 Redis 缓存，
 * 命中时不再查 MySQL 装配 SPU+SKU，显著降低高并发下连接池压力。
 */
@Slf4j
@Tag(name = "商品")
@RestController
@RequestMapping("/product")
@RequiredArgsConstructor
public class ProductController {

    private final ProductService productService;
    private final MerchantForecastService merchantForecastService;
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    @Operation(summary = "商家销量预测（7/30 天，演示口径）")
    @GetMapping("/merchant/forecast")
    public Result<List<Map<String, Object>>> forecast(@RequestParam(defaultValue = "7") int days) {
        return Result.ok(merchantForecastService.forecast(days));
    }

    @Operation(summary = "商家智能选品（热度/库存/趋势，演示口径）")
    @GetMapping("/merchant/selection")
    public Result<List<Map<String, Object>>> selection() {
        return Result.ok(merchantForecastService.smartSelection());
    }

    /** 分页缓存 TTL（秒），默认 60，可配 product.cache.ttl-seconds */
    @Value("${product.cache.ttl-seconds:60}")
    private long cacheTtlSeconds;

    private static final String PAGE_CACHE_PREFIX = "prod:page:";

    @Operation(summary = "分页查商品")
    @GetMapping("/page")
    public Result<Page<SpuDetailResponse>> page(SpuPageQuery query) {
        String key = pageCacheKey(query);
        // 1) 缓存命中直接返回（高并发读接口，避免重复查库装配）
        String cached = redisTemplate.opsForValue().get(key);
        if (cached != null) {
            try {
                @SuppressWarnings("unchecked")
                Result<Page<SpuDetailResponse>> hit = objectMapper.readValue(cached, Result.class);
                return hit;
            } catch (Exception e) {
                log.warn("product page 缓存反序列化失败, key={}, err={}", key, e.getMessage());
            }
        }
        // 2) 未命中查库并回填缓存（空结果不缓存，防穿透）
        Result<Page<SpuDetailResponse>> result = Result.ok(productService.page(query));
        if (result.getData() != null && !result.getData().getRecords().isEmpty()) {
            try {
                redisTemplate.opsForValue().set(key, objectMapper.writeValueAsString(result),
                        Duration.ofSeconds(cacheTtlSeconds));
            } catch (Exception e) {
                log.warn("product page 缓存写入失败, err={}", e.getMessage());
            }
        }
        return result;
    }

    @Operation(summary = "商品详情")
    @GetMapping("/{spuId}")
    public Result<SpuDetailResponse> detail(@PathVariable("spuId") Long spuId) {
        return Result.ok(productService.getDetail(spuId));
    }

    @Operation(summary = "新增spu")
    @PostMapping("/spu")
    public Result<Map<String, String>> create(@Valid @RequestBody SpuCreateRequest request) {
        // spuId 为 Snowflake ID，转字符串返回避免前端精度丢失
        evictPageCache();
        return Result.ok(Map.of("spuId", String.valueOf(productService.createSpu(request))));
    }

    @Operation(summary = "更新spu")
    @PutMapping("/spu/{spuId}")
    public Result<Void> update(@PathVariable("spuId") Long spuId, @RequestBody SpuCreateRequest request) {
        productService.updateSpu(spuId, request);
        evictPageCache();
        return Result.ok();
    }

    @Operation(summary = "下架")
    @DeleteMapping("/spu/{spuId}")
    public Result<Void> offShelf(@PathVariable("spuId") Long spuId) {
        productService.offShelf(spuId);
        evictPageCache();
        return Result.ok();
    }

    @Operation(summary = "分类树")
    @GetMapping("/category/tree")
    public Result<List<Category>> categoryTree() {
        return Result.ok(productService.categoryTree());
    }

    @Operation(summary = "品牌列表")
    @GetMapping("/brands")
    public Result<List<Brand>> brands() {
        return Result.ok(productService.brands());
    }

    @Operation(summary = "校验SKU是否存在")
    @GetMapping("/sku/{skuId}/validate")
    public Result<Boolean> validateSku(@PathVariable("skuId") Long skuId) {
        return Result.ok(productService.validateSku(skuId));
    }

    @Operation(summary = "按 skuId 查 SKU 详情")
    @GetMapping("/sku/{skuId}")
    public Result<SpuDetailResponse.SkuItem> querySku(@PathVariable("skuId") Long skuId) {
        ProductSku sku = productService.querySku(skuId);
        if (sku == null) return Result.ok(null);
        return Result.ok(SpuDetailResponse.SkuItem.builder()
                .skuId(sku.getSkuId())
                .spuId(sku.getSpuId())
                .specName(sku.getSpecName())
                .specValue(sku.getSpecValue())
                .price(sku.getPrice())
                .stock(sku.getStock())
                .image(sku.getImage())
                .build());
    }

    /** 写操作后清空分页缓存（商品列表可能已变化） */
    private void evictPageCache() {
        try {
            var keys = redisTemplate.keys(PAGE_CACHE_PREFIX + "*");
            if (keys != null && !keys.isEmpty()) {
                redisTemplate.delete(keys);
            }
        } catch (Exception e) {
            log.warn("product page 缓存清理失败, err={}", e.getMessage());
        }
    }

    private String pageCacheKey(SpuPageQuery q) {
        return PAGE_CACHE_PREFIX
                + (q.getPageNum() == null ? 1 : q.getPageNum()) + ":"
                + (q.getPageSize() == null ? 20 : q.getPageSize()) + ":"
                + (q.getKeyword() == null ? "" : q.getKeyword()) + ":"
                + (q.getCategoryId() == null ? "" : q.getCategoryId()) + ":"
                + (q.getBrandId() == null ? "" : q.getBrandId());
    }
}
