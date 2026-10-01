package com.zhigou.user.controller;

import com.zhigou.common.Result;
import com.zhigou.user.dto.AddressRequest;
import com.zhigou.user.dto.AddressResponse;
import com.zhigou.user.interceptor.UserContext;
import com.zhigou.user.service.AddressService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "收货地址")
@RestController
@RequestMapping("/address")
@RequiredArgsConstructor
public class AddressController {

    private final AddressService addressService;

    @Operation(summary = "地址列表")
    @GetMapping("/list")
    public Result<List<AddressResponse>> list() {
        Long userId = UserContext.requireUserId();
        return Result.ok(addressService.listByUserId(userId));
    }

    @Operation(summary = "新增地址")
    @PostMapping
    public Result<AddressResponse> add(@Valid @RequestBody AddressRequest request) {
        Long userId = UserContext.requireUserId();
        return Result.ok(addressService.add(userId, request));
    }

    @Operation(summary = "修改地址")
    @PutMapping("/{addressId}")
    public Result<AddressResponse> update(@PathVariable Long addressId,
                                           @RequestBody AddressRequest request) {
        Long userId = UserContext.requireUserId();
        return Result.ok(addressService.update(userId, addressId, request));
    }

    @Operation(summary = "删除地址")
    @DeleteMapping("/{addressId}")
    public Result<Void> delete(@PathVariable Long addressId) {
        Long userId = UserContext.requireUserId();
        addressService.delete(userId, addressId);
        return Result.ok();
    }

    @Operation(summary = "设为默认地址")
    @PutMapping("/{addressId}/default")
    public Result<Void> setDefault(@PathVariable Long addressId) {
        Long userId = UserContext.requireUserId();
        addressService.setDefault(userId, addressId);
        return Result.ok();
    }
}