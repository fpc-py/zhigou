package com.zhigou.marketing.service;
import com.zhigou.marketing.entity.UserCoupon;
import java.util.List;
public interface CouponService {
    Long issue(Long userId, Long templateId);
    List<UserCoupon> mine(Long userId, String status);
    void preFreeze(Long userId, Long couponId, Long orderId);
    void confirm(Long orderId);
    void release(Long orderId);
}