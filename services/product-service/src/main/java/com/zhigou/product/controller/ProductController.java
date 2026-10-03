package com.zhigou.product.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zhigou.common.Result;
import com.zhigou.product.dto.SpuCreateRequest;
import com.zhigou.product.dto.SpuDetailResponse;
import com.zhigou.product.dto.SpuPageQuery;
import com.zhigou.product.entity.Brand;
import com.zhigou.product.entity.Category;
import com.zhigou.product.entity.ProductSku;
import com.zhigou.product.service.ProductService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@Tag(name = "商品")
@RestController
@RequestMapping("/product")
@RequiredArgsConstructor
public class ProductController {

    private final ProductService productService;

    @Operation(summary = "分页查商品")
    @GetMapping("/page")
    public Result<Page<SpuDetailResponse>> page(SpuPageQuery query) {
        return Result.ok(productService.page(query));
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
        return Result.ok(Map.of("spuId", String.valueOf(productService.createSpu(request))));
    }

    @Operation(summary = "更新spu")
    @PutMapping("/spu/{spuId}")
    public Result<Void> update(@PathVariable("spuId") Long spuId, @RequestBody SpuCreateRequest request) {
        productService.updateSpu(spuId, request);
        return Result.ok();
    }

    @Operation(summary = "下架")
    @DeleteMapping("/spu/{spuId}")
    public Result<Void> offShelf(@PathVariable("spuId") Long spuId) {
        productService.offShelf(spuId);
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
                .specName(sku.getSpecName())
                .specValue(sku.getSpecValue())
                .price(sku.getPrice())
                .stock(sku.getStock())
                .image(sku.getImage())
                .build());
    }
}