package com.zhigou.cart.controller;

import com.zhigou.cart.dto.CartAddRequest;
import com.zhigou.cart.dto.CartItemResponse;
import com.zhigou.cart.dto.CartUpdateRequest;
import com.zhigou.cart.interceptor.UserContext;
import com.zhigou.cart.service.CartService;
import com.zhigou.common.Result;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@Tag(name = "购物车") @RestController @RequestMapping("/cart") @RequiredArgsConstructor
public class CartController {
    private final CartService cartService;

    @Operation(summary = "加购") @PostMapping("/add")
    public Result<Void> add(@Valid @RequestBody CartAddRequest req) {
        cartService.add(UserContext.require(), req); return Result.ok();
    }

    @Operation(summary = "改数量/选中") @PostMapping("/update")
    public Result<Void> update(@Valid @RequestBody CartUpdateRequest req) {
        cartService.update(UserContext.require(), req); return Result.ok();
    }

    @Operation(summary = "我的购物车") @GetMapping("/mine")
    public Result<List<CartItemResponse>> mine() {
        return Result.ok(cartService.mine(UserContext.require()));
    }

    @Operation(summary = "删除单条") @DeleteMapping("/{skuId}")
    public Result<Void> remove(@PathVariable Long skuId) {
        cartService.remove(UserContext.require(), skuId); return Result.ok();
    }

    @Operation(summary = "清空选中") @PostMapping("/clear")
    public Result<Void> clear() { cartService.clearSelected(UserContext.require()); return Result.ok(); }
}