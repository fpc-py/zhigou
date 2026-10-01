package com.zhigou.user.service;

import com.zhigou.user.dto.AddressRequest;
import com.zhigou.user.dto.AddressResponse;

import java.util.List;

public interface AddressService {

    List<AddressResponse> listByUserId(Long userId);

    AddressResponse add(Long userId, AddressRequest request);

    AddressResponse update(Long userId, Long addressId, AddressRequest request);

    void delete(Long userId, Long addressId);

    void setDefault(Long userId, Long addressId);
}