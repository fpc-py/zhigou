package com.zhigou.product.service.impl;

import cn.hutool.core.util.IdUtil;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhigou.common.BizException;
import com.zhigou.product.dto.SpuCreateRequest;
import com.zhigou.product.dto.SpuDetailResponse;
import com.zhigou.product.dto.SpuPageQuery;
import com.zhigou.product.entity.Brand;
import com.zhigou.product.entity.Category;
import com.zhigou.product.entity.ProductSku;
import com.zhigou.product.entity.ProductSpu;
import com.zhigou.product.mapper.*;
import com.zhigou.product.service.ProductService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ProductServiceImpl implements ProductService {

    private final ProductSpuMapper spuMapper;
    private final ProductSkuMapper skuMapper;
    private final CategoryMapper categoryMapper;
    private final BrandMapper brandMapper;
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final RocketMQTemplate rocketMQTemplate;

    private static final String DETAIL_CACHE_KEY = "product:detail:";
    private static final long CACHE_TTL = 5;

    /** B1 L1 本地缓存：热点 SPU 详情（TTL 60s，max 10000）；get(key,loader) 原子加载 = 并发单飞防击穿 */
    private final Cache<String, Optional<SpuDetailResponse>> localCache = Caffeine.newBuilder()
            .maximumSize(10_000)
            .expireAfterWrite(60, TimeUnit.SECONDS)
            .build();

    @Override
    public Page<SpuDetailResponse> page(SpuPageQuery query) {
        Page<ProductSpu> page = new Page<>(query.getPageNum(), query.getPageSize());
        LambdaQueryWrapper<ProductSpu> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ProductSpu::getStatus, 1); // 只查上架

        if (query.getCategoryId() != null) wrapper.eq(ProductSpu::getCategoryId, query.getCategoryId());
        if (query.getBrandId() != null) wrapper.eq(ProductSpu::getBrandId, query.getBrandId());
        if (query.getKeyword() != null && !query.getKeyword().isBlank())
            wrapper.like(ProductSpu::getName, query.getKeyword());
        // 多商家隔离：商家管理端只查本商家商品；消费者端（merchantId=null）跨商家浏览
        if (query.getMerchantId() != null) {
            wrapper.eq(ProductSpu::getMerchantId, query.getMerchantId());
        }

        wrapper.orderByDesc(ProductSpu::getSalesVolume);
        Page<ProductSpu> spuPage = spuMapper.selectPage(page, wrapper);

        Page<SpuDetailResponse> result = new Page<>(query.getPageNum(), query.getPageSize(), spuPage.getTotal());
        result.setRecords(spuPage.getRecords().stream().map(this::buildDetail).collect(Collectors.toList()));
        return result;
    }

    @Override
    public SpuDetailResponse getDetail(Long spuId) {
        String cacheKey = DETAIL_CACHE_KEY + spuId;
        // B1 L1 Caffeine：get(key, loader) 同一 key 并发只执行一次 loader（单飞，防击穿）；空值 Optional 短缓存（防穿透）
        Optional<SpuDetailResponse> local = localCache.get(cacheKey, k -> loadWithRedis(k, spuId));
        return local.orElse(null);
    }

    /** L2 Redis → DB 加载（由 Caffeine 单飞保证同一 key 仅一个线程进入） */
    private Optional<SpuDetailResponse> loadWithRedis(String cacheKey, Long spuId) {
        String cached = redisTemplate.opsForValue().get(cacheKey);
        if (cached != null) {
            try {
                return Optional.ofNullable(objectMapper.readValue(cached, SpuDetailResponse.class));
            } catch (Exception e) {
                log.warn("L2 反序列化失败: spuId={}", spuId);
            }
        }

        SpuDetailResponse detail = loadDetail(spuId);
        if (detail == null) {
            return Optional.empty();
        }
        try {
            redisTemplate.opsForValue().set(cacheKey, objectMapper.writeValueAsString(detail), CACHE_TTL, TimeUnit.MINUTES);
        } catch (Exception e) {
            log.warn("L2 写入失败: spuId={}", spuId);
        }
        return Optional.of(detail);
    }

    @Override
    @Transactional
    public Long createSpu(SpuCreateRequest request, Long merchantId) {
        long spuId = IdUtil.getSnowflakeNextId();

        ProductSpu spu = new ProductSpu();
        spu.setSpuId(spuId);
        spu.setCategoryId(request.getCategoryId());
        spu.setBrandId(request.getBrandId());
        spu.setMerchantId(merchantId); // 多商家隔离：记录归属商家
        spu.setName(request.getName());
        spu.setSubtitle(request.getSubtitle());
        spu.setMainImage(request.getMainImage());
        spu.setDescription(request.getDescription());
        spu.setStatus(1);
        if (request.getSkus() != null && !request.getSkus().isEmpty()) {
            long minP = request.getSkus().stream().mapToLong(SpuCreateRequest.SkuItem::getPrice).min().orElse(0);
            long maxP = request.getSkus().stream().mapToLong(SpuCreateRequest.SkuItem::getPrice).max().orElse(0);
            spu.setPriceMin(minP);
            spu.setPriceMax(maxP);
        }
        spuMapper.insert(spu);

        if (request.getSkus() != null) {
            for (SpuCreateRequest.SkuItem s : request.getSkus()) {
                ProductSku sku = new ProductSku();
                sku.setSkuId(IdUtil.getSnowflakeNextId());
                sku.setSpuId(spuId);
                sku.setSpecName(s.getSpecName());
                sku.setSpecValue(s.getSpecValue());
                sku.setPrice(s.getPrice());
                sku.setStock(s.getStock() != null ? s.getStock() : 0);
                sku.setImage(s.getImage());
                skuMapper.insert(sku);
            }
        }
        log.info("SPU 创建: spuId={}", spuId);

        // 发送 MQ 消息触发 RAG 向量同步
        try {
            rocketMQTemplate.convertAndSend("PRODUCT_CHANGED",
                Map.of("spuId", spuId, "name", request.getName(),
                       "subtitle", request.getSubtitle() != null ? request.getSubtitle() : "",
                       "description", request.getDescription() != null ? request.getDescription() : ""));
        } catch (Exception e) {
            log.warn("PRODUCT_CHANGED MQ 发送失败: spuId={}, {}", spuId, e.getMessage());
        }

        return spuId;
    }

    @Override
    @Transactional
    public void updateSpu(Long spuId, SpuCreateRequest request, Long merchantId) {
        ProductSpu spu = requireOwnSpu(spuId, merchantId);
        if (request.getName() != null) spu.setName(request.getName());
        if (request.getSubtitle() != null) spu.setSubtitle(request.getSubtitle());
        if (request.getMainImage() != null) spu.setMainImage(request.getMainImage());
        if (request.getDescription() != null) spu.setDescription(request.getDescription());
        spuMapper.updateById(spu);

        // 删缓存
        redisTemplate.delete(DETAIL_CACHE_KEY + spuId);
        localCache.invalidate(DETAIL_CACHE_KEY + spuId);

        // 发送 MQ 消息触发 RAG 向量同步
        try {
            rocketMQTemplate.convertAndSend("PRODUCT_CHANGED",
                Map.of("spuId", spuId, "name", spu.getName(),
                       "subtitle", spu.getSubtitle() != null ? spu.getSubtitle() : "",
                       "description", spu.getDescription() != null ? spu.getDescription() : ""));
        } catch (Exception e) {
            log.warn("PRODUCT_CHANGED MQ 发送失败: spuId={}, {}", spuId, e.getMessage());
        }

        log.info("SPU 更新: spuId={}", spuId);
    }

    @Override
    public void offShelf(Long spuId, Long merchantId) {
        ProductSpu spu = requireOwnSpu(spuId, merchantId);
        spu.setStatus(0);
        spuMapper.updateById(spu);
        redisTemplate.delete(DETAIL_CACHE_KEY + spuId);
        localCache.invalidate(DETAIL_CACHE_KEY + spuId);

        try {
            rocketMQTemplate.convertAndSend("PRODUCT_CHANGED",
                Map.of("spuId", spuId, "action", "DELETE"));
        } catch (Exception e) {
            log.warn("PRODUCT_CHANGED MQ 发送失败: spuId={}, {}", spuId, e.getMessage());
        }

        log.info("SPU 下架: spuId={}", spuId);
    }

    @Override
    public List<Category> categoryTree() {
        return categoryMapper.selectList(null);
    }

    @Override
    public List<Brand> brands() {
        return brandMapper.selectList(new LambdaQueryWrapper<Brand>().eq(Brand::getStatus, 1));
    }

    // ===== Private =====

    private SpuDetailResponse loadDetail(Long spuId) {
        ProductSpu spu = spuMapper.selectOne(
                new LambdaQueryWrapper<ProductSpu>().eq(ProductSpu::getSpuId, spuId));
        if (spu == null) return null;
        return buildDetail(spu);
    }

    private SpuDetailResponse buildDetail(ProductSpu spu) {
        List<ProductSku> skus = skuMapper.selectList(
                new LambdaQueryWrapper<ProductSku>().eq(ProductSku::getSpuId, spu.getSpuId()));
        return SpuDetailResponse.builder()
                .spuId(spu.getSpuId()).name(spu.getName()).subtitle(spu.getSubtitle())
                .mainImage(spu.getMainImage()).description(spu.getDescription())
                .status(spu.getStatus()).priceMin(spu.getPriceMin()).priceMax(spu.getPriceMax())
                .salesVolume(spu.getSalesVolume()).categoryId(spu.getCategoryId()).brandId(spu.getBrandId())
                .merchantId(spu.getMerchantId())
                .skus(skus.stream().map(s -> SpuDetailResponse.SkuItem.builder()
                        .skuId(s.getSkuId()).specName(s.getSpecName()).specValue(s.getSpecValue())
                        .price(s.getPrice()).stock(s.getStock()).image(s.getImage()).build()
                ).collect(Collectors.toList()))
                .build();
    }

    private ProductSpu requireSpu(Long spuId) {
        ProductSpu spu = spuMapper.selectOne(
                new LambdaQueryWrapper<ProductSpu>().eq(ProductSpu::getSpuId, spuId));
        if (spu == null) throw new BizException(404, "商品不存在");
        return spu;
    }

    /**
     * 归属校验（多商家隔离）：商家只能操作自己名下的商品。
     * merchantId 为 null（平台直连/调试）时跳过校验以兼容存量调用。
     */
    private ProductSpu requireOwnSpu(Long spuId, Long merchantId) {
        ProductSpu spu = requireSpu(spuId);
        if (merchantId != null && spu.getMerchantId() != null && !spu.getMerchantId().equals(merchantId)) {
            throw new BizException(403, "无权操作其他商家的商品");
        }
        return spu;
    }

    @Override
    public boolean validateSku(Long skuId) {
        if (skuId == null) return false;
        ProductSku sku = skuMapper.selectOne(
                new LambdaQueryWrapper<ProductSku>().eq(ProductSku::getSkuId, skuId));
        return sku != null;
    }

    @Override
    public ProductSku querySku(Long skuId) {
        if (skuId == null) return null;
        return skuMapper.selectOne(
                new LambdaQueryWrapper<ProductSku>().eq(ProductSku::getSkuId, skuId));
    }
}