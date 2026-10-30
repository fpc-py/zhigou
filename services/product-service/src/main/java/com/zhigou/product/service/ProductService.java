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
    /** 新增 SPU；merchantId 为归属商家（多商家隔离；null=平台全局/直连调试） */
    Long createSpu(SpuCreateRequest request, Long merchantId);
    /** 更新 SPU；merchantId 用于归属校验（非本商家商品拒绝操作） */
    void updateSpu(Long spuId, SpuCreateRequest request, Long merchantId);
    /** 下架 SPU；merchantId 用于归属校验（非本商家商品拒绝操作） */
    void offShelf(Long spuId, Long merchantId);
    List<Category> categoryTree();
    List<Brand> brands();
    /** 校验 SKU 是否存在（供 cart-service 等下游服务调用） */
    boolean validateSku(Long skuId);
    /** 按 skuId 查询 SKU 详情（供 AI 导购等下游服务调用） */
    ProductSku querySku(Long skuId);
}