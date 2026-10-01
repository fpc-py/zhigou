package com.zhigou.marketing.dto;
import lombok.AllArgsConstructor; import lombok.Builder; import lombok.Data; import lombok.NoArgsConstructor;
import java.util.List;
@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class CalculateResponse {
    private Long totalAmount; private Long discountAmount; private Long finalAmount;
    private List<Detail> detail; private List<AvailableCoupon> availableCoupons;
    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class Detail { private String ruleName; private Long discountAmount; }
    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class AvailableCoupon { private Long couponId; private Long saveAmount; }
}