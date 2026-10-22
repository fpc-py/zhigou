import http from './request';

/** AI 省钱助手（C 组 P0）——overview / 历史价格 / 降价关注与提醒 */

export interface CostSaverOverview {
  cartDiscountSaving: number; // 分
  couponBestSaving: number;
  usableCouponCount: number;
  compareSaving: number;
  bestSource: string;
  noticeSaving: number;
  unreadNotices: number;
  watchCount: number;
  cartItemCount: number;
  totalSaving: number;
  summary: string;
  recentNotices: PriceNotice[];
}

export interface PriceNotice {
  id: string;
  skuId: string;
  skuName: string;
  oldPrice: number;
  newPrice: number;
  dropAmount: number;
  readFlag: number;
  createdAt: string;
}

export interface PriceAlertItem {
  id: string;
  skuId: string;
  skuName: string;
  watchPrice: number;
  currentPrice: number;
  targetPrice: number | null;
  dropped: boolean;
  dropAmount: number;
  status: string;
  updatedAt: string;
}

export interface PriceHistory {
  skuId: string;
  skuName: string;
  currentPrice: number;
  lowestPrice: number;
  lowestDate: string | null;
  diffFromLowest: number;
  dailyPrices: Record<string, number>;
  sampledDays: number;
  summary: string;
}

/** 省钱总览（第一屏可感知） */
export async function fetchOverview(): Promise<CostSaverOverview | null> {
  const res = await http.get<{ code: number; data: CostSaverOverview }>('/cost-saver/overview');
  return res.data?.data ?? null;
}

/** 历史价格趋势 */
export async function fetchHistory(skuId: string, days = 30): Promise<PriceHistory | null> {
  const res = await http.get<{ code: number; data: PriceHistory }>(`/price/history?skuId=${skuId}&days=${days}`);
  return res.data?.data ?? null;
}

/** 关注降价（targetPrice 可空=任意降价） */
export async function watchSku(skuId: string, targetPrice?: number | null): Promise<PriceAlertItem | null> {
  const res = await http.post<{ code: number; data: PriceAlertItem }>('/price/alert', { skuId, targetPrice: targetPrice ?? null });
  return res.data?.data ?? null;
}

/** 取消关注 */
export async function unwatchSku(skuId: string): Promise<void> {
  await http.delete(`/price/alert/${skuId}`);
}

/** 我的关注列表 */
export async function fetchAlerts(): Promise<PriceAlertItem[]> {
  const res = await http.get<{ code: number; data: PriceAlertItem[] }>('/price/alert/mine');
  return res.data?.data ?? [];
}

/** 我的降价提醒 */
export async function fetchNotices(unreadOnly = false): Promise<PriceNotice[]> {
  const res = await http.get<{ code: number; data: PriceNotice[] }>(`/price/notice/mine?unreadOnly=${unreadOnly}`);
  return res.data?.data ?? [];
}

/** 标记提醒已读 */
export async function markNoticeRead(noticeId: string): Promise<void> {
  await http.post(`/price/notice/read/${noticeId}`);
}
