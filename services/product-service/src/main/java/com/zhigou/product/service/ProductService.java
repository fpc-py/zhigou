package com.zhigou.product.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zhigou.product.dto.SpuCreateRequest;
import com.zhigou.product.dto.SpuDetailResponse;
import com.zhigou.product.dto.SpuPageQuery;
import com.zhigou.product.entity.Brand;
import com.zhigou.product.entity.Category;
import com.zhigou.product.entity.ProductSku;

import java.util.List;

public interface ProductService {
    Page<SpuDetailResponse> page(SpuPageQuery query);
    SpuDetailResponse getDetail(Long spuId);
    Long createSpu(SpuCreateRequest request);
    void updateSpu(Long spuId, SpuCreateRequest request);
    void offShelf(Long spuId);
    List<Category> categoryTree();
    List<Brand> brands();
    /** 校验 SKU 是否存在（供 cart-service 等下游服务调用） */
    boolean validateSku(Long skuId);
    /** 按 skuId 查询 SKU 详情（供 AI 导购等下游服务调用） */
    ProductSku querySku(Long skuId);
}