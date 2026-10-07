/** /coupon /discount 响应体类型（透传 marketing-service） */

export interface UserCoupon {
  id: string;
  userId: string;
  couponTemplateId: string;
  status: string;
  sourceOrderId?: string | null;
  lockedAt?: string | null;
  usedAt?: string | null;
  createTime?: string;
}

export interface DiscountCalculateBody {
  items: Array<{ skuId: string; count: number; price: number }>;
  couponId?: number | null;
}

export interface DiscountDetail {
  ruleName: string;
  discountAmount: number;
}

export interface AvailableCoupon {
  couponId: string;
  saveAmount: number;
}

export interface DiscountCalculateResult {
  totalAmount: number;
  discountAmount: number;
  finalAmount: number;
  detail: DiscountDetail[];
  availableCoupons: AvailableCoupon[];
}
