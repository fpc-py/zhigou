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
  /** 坑 #63：couponId 19 位超 JS 安全整数——统一字符串透传 */
  couponId?: string | null;
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
