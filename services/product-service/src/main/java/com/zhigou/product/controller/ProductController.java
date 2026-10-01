package com.zhigou.product.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zhigou.common.Result;
import com.zhigou.product.dto.SpuCreateRequest;
import com.zhigou.product.dto.SpuDetailResponse;
import com.zhigou.product.dto.SpuPageQuery;
import com.zhigou.product.entity.Brand;
import com.zhigou.product.entity.Category;
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
    public Result<Map<String, Long>> create(@Valid @RequestBody SpuCreateRequest request) {
        return Result.ok(Map.of("spuId", productService.createSpu(request)));
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
}