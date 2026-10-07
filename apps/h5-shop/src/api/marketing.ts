import http from './request';

export interface UserCoupon {
  id: string;
  userId: string;
  couponTemplateId: string;
  status: string;
  createTime?: string;
}

export interface DiscountDetail {
  ruleName: string;
  discountAmount: number;
}

export interface AvailableCoupon {
  couponId: string;
  saveAmount: number;
}

export interface DiscountResult {
  totalAmount: number;
  discountAmount: number;
  finalAmount: number;
  detail: DiscountDetail[];
  availableCoupons: AvailableCoupon[];
}

/** 我的优惠券 */
export async function getMyCoupons(status?: string): Promise<UserCoupon[]> {
  const res = await http.get<{ code: number; data: UserCoupon[] }>('/coupon/mine', {
    params: status ? { status } : {},
  });
  return res.data.data ?? [];
}

/** 优惠试算（满减/优惠券） */
export async function calculateDiscount(body: {
  items: Array<{ skuId: string; count: number; price: number }>;
  couponId?: number | null;
}): Promise<DiscountResult | null> {
  const res = await http.post<{ code: number; data: DiscountResult | null }>('/discount/calculate', body);
  return res.data.data;
}
