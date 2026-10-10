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

// ==================== 拼团 ====================

export interface GroupBuyGroup {
  groupId: string;
  leaderUserId: string;
  memberCount: number;
  targetSize: number;
  remain: number;
  status: string;
  expireTime?: string;
}

export interface GroupBuyActivity {
  id: string;
  skuId: string;
  spuId: string;
  title: string;
  imageUrl?: string;
  soloPrice: number;
  groupPrice: number;
  groupSize: number;
  limitMinutes: number;
  startTime?: string;
  endTime?: string;
  openGroups: GroupBuyGroup[];
}

export interface GroupBuyOrder {
  id: string;
  activityId: string;
  leaderUserId: string;
  targetSize: number;
  status: string;
  expireTime?: string;
}

/** 拼团活动列表 */
export async function getGroupBuyActivities(): Promise<GroupBuyActivity[]> {
  const res = await http.get<{ code: number; data: GroupBuyActivity[] }>('/group-buy/activities');
  return res.data.data ?? [];
}

/** 开团 */
export async function openGroupBuy(activityId: string): Promise<GroupBuyOrder | null> {
  const res = await http.post<{ code: number; data: GroupBuyOrder | null }>('/group-buy/open', { activityId });
  return res.data.data;
}

/** 参团 */
export async function joinGroupBuy(groupId: string): Promise<GroupBuyOrder | null> {
  const res = await http.post<{ code: number; data: GroupBuyOrder | null }>('/group-buy/join', { groupId });
  return res.data.data;
}

/** 我的团单 */
export async function getMyGroupBuys(): Promise<Array<Record<string, any>>> {
  const res = await http.get<{ code: number; data: Array<Record<string, any>> }>('/group-buy/mine');
  return res.data.data ?? [];
}
