package com.zhigou.cart.service;

import com.zhigou.cart.dto.CartAddRequest;
import com.zhigou.cart.dto.CartItemResponse;
import com.zhigou.cart.dto.CartUpdateRequest;
import java.util.List;

public interface CartService {
    void add(Long userId, CartAddRequest req);
    void update(Long userId, CartUpdateRequest req);
    List<CartItemResponse> mine(Long userId);
    void remove(Long userId, Long skuId);
    void clearSelected(Long userId);
}