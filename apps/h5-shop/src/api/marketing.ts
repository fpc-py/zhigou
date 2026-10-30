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

/** 领取优惠券（templateId 后端 long，前端字符串透传） */
export async function claimCoupon(templateId: string): Promise<number | null> {
  const res = await http.post<{ code: number; data: number | null }>('/coupon/issue', { templateId });
  return res.data.data;
}

/** 优惠券模板（领券中心展示；与 marketing-service 种子数据一致） */
export const COUPON_TEMPLATES = [
  { id: '1', title: '满 200 减 30', rule: '订单满 ¥200 可用，全场通用', badge: '满减券' },
  { id: '2', title: '8 折券', rule: '不限门槛，最高减 ¥100', badge: '折扣券' },
  { id: '3', title: '直减 5 元', rule: '无门槛立减 ¥5', badge: '无门槛' },
];

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

// ==================== 秒杀 ====================

export interface SeckillActivity {
  id: string;
  skuId: string;
  title: string;
  imageUrl?: string;
  seckillPrice: number;
  originPrice: number;
  total: number;
  sold: number;
  perUserLimit: number;
  startTime?: string;
  endTime?: string;
  status: number;
}

/** 秒杀活动列表 */
export async function getSeckillActivities(): Promise<SeckillActivity[]> {
  const res = await http.get<{ code: number; data: SeckillActivity[] }>('/seckill/activities');
  return res.data.data ?? [];
}

/** 秒杀预扣下单（防超卖 + 限购在营销服务端校验） */
export async function seckillBuy(activityId: string, count = 1): Promise<Record<string, any> | null> {
  const res = await http.post<{ code: number; data: Record<string, any> | null }>('/seckill/buy', {
    activityId,
    count,
  });
  return res.data.data;
}

/** 释放秒杀预扣（下单失败/超时未支付时归还库存） */
export async function releaseSeckill(activityId: string, count = 1): Promise<void> {
  await http.post('/seckill/release', { activityId, count });
}
