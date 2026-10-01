package com.zhigou.user.service.impl;

import cn.hutool.core.util.IdUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.zhigou.common.BizException;
import com.zhigou.user.dto.AddressRequest;
import com.zhigou.user.dto.AddressResponse;
import com.zhigou.user.entity.Address;
import com.zhigou.user.mapper.AddressMapper;
import com.zhigou.user.service.AddressService;
import com.zhigou.user.util.PhoneEncryptUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class AddressServiceImpl implements AddressService {

    private final AddressMapper addressMapper;

    @Value("${phone.secret}")
    private String phoneSecret;

    @Override
    public List<AddressResponse> listByUserId(Long userId) {
        List<Address> list = addressMapper.selectList(
                new LambdaQueryWrapper<Address>().eq(Address::getUserId, userId)
        );
        return list.stream().map(this::toResponse).collect(Collectors.toList());
    }

    @Override
    public AddressResponse add(Long userId, AddressRequest request) {
        long count = addressMapper.selectCount(
                new LambdaQueryWrapper<Address>().eq(Address::getUserId, userId)
        );

        Address addr = new Address();
        addr.setAddressId(IdUtil.getSnowflakeNextId());
        addr.setUserId(userId);
        addr.setReceiverName(request.getReceiverName());
        addr.setReceiverPhone(PhoneEncryptUtil.encrypt(request.getReceiverPhone(), phoneSecret));
        addr.setProvince(request.getProvince());
        addr.setCity(request.getCity());
        addr.setDistrict(request.getDistrict());
        addr.setDetail(request.getDetail());
        addr.setIsDefault(count == 0 ? 1 : 0);  // 第一个地址自动设为默认

        addressMapper.insert(addr);
        log.info("新增地址: addressId={}, userId={}", addr.getAddressId(), userId);
        return toResponse(addr);
    }

    @Override
    public AddressResponse update(Long userId, Long addressId, AddressRequest request) {
        Address addr = requireOwnAddress(userId, addressId);

        if (request.getReceiverName() != null) addr.setReceiverName(request.getReceiverName());
        if (request.getReceiverPhone() != null) addr.setReceiverPhone(PhoneEncryptUtil.encrypt(request.getReceiverPhone(), phoneSecret));
        if (request.getProvince() != null) addr.setProvince(request.getProvince());
        if (request.getCity() != null) addr.setCity(request.getCity());
        if (request.getDistrict() != null) addr.setDistrict(request.getDistrict());
        if (request.getDetail() != null) addr.setDetail(request.getDetail());

        addressMapper.updateById(addr);
        log.info("修改地址: addressId={}", addressId);
        return toResponse(addr);
    }

    @Override
    public void delete(Long userId, Long addressId) {
        Address addr = requireOwnAddress(userId, addressId);
        addressMapper.deleteById(addr.getId());
        log.info("删除地址: addressId={}", addressId);
    }

    @Override
    @Transactional
    public void setDefault(Long userId, Long addressId) {
        Address addr = requireOwnAddress(userId, addressId);

        // 同事务：清除该用户所有地址的 is_default
        addressMapper.update(null,
                new LambdaUpdateWrapper<Address>()
                        .eq(Address::getUserId, userId)
                        .set(Address::getIsDefault, 0)
        );

        // 设置新默认
        addr.setIsDefault(1);
        addressMapper.updateById(addr);
        log.info("设为默认地址: addressId={}, userId={}", addressId, userId);
    }

    /**
     * 校验地址归属：address 必须属于当前 userId，否则抛 403
     */
    private Address requireOwnAddress(Long userId, Long addressId) {
        Address addr = addressMapper.selectOne(
                new LambdaQueryWrapper<Address>()
                        .eq(Address::getAddressId, addressId)
                        .eq(Address::getUserId, userId)
        );
        if (addr == null) {
            throw new BizException(403, "无权操作该地址");
        }
        return addr;
    }

    private AddressResponse toResponse(Address a) {
        String plainPhone = PhoneEncryptUtil.decrypt(a.getReceiverPhone(), phoneSecret);
        return AddressResponse.builder()
                .addressId(a.getAddressId())
                .receiverName(a.getReceiverName())
                .receiverPhone(plainPhone)
                .province(a.getProvince())
                .city(a.getCity())
                .district(a.getDistrict())
                .detail(a.getDetail())
                .isDefault(a.getIsDefault())
                .build();
    }
}