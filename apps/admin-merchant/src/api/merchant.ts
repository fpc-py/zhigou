/**
 * 商家经营 API（BFF 透传；单商家市场 = 平台聚合演示口径）
 * 覆盖：经营概览/预警/履约异常/补货中心/经营大脑/评论管理
 */
import http from './request';

export interface MerchantOverview {
  totalOrders: number;
  totalSalesFen: number;
  todayOrders: number;
  todaySalesFen: number;
  statusDist: Record<string, number>;
  hotSpus: { spuId: number; spuName: string; soldCount: number; salesFen: number }[];
  pendingAfterSale: number;
  generatedAt: string;
}

/** 商家经营概览 */
export async function getMerchantOverview(): Promise<MerchantOverview | null> {
  const res = await http.get('/merchant/overview');
  return res.data?.data ?? null;
}

export interface StockLow { skuId: number; available: number }
export interface ReviewNegative { spuId: string; rating: number; content: string; userName?: string; createTime?: string }
export interface MerchantWarnings {
  lowStock: StockLow[];
  negative: ReviewNegative[];
}

/** 商家经营预警（低库存 + 差评） */
export async function getMerchantWarnings(): Promise<MerchantWarnings | null> {
  const res = await http.get('/merchant/warnings');
  return res.data?.data ?? null;
}

export interface FulfillItem { skuId: number; skuName: string; count: number; price: number }
export interface PendingOrder {
  orderId: string;
  orderStatus: string;
  payAmount?: number;
  handled?: boolean;
  items: FulfillItem[];
}

/** 履约异常：PAID 待发货订单 + SKU 明细（缺货/卡单预警） */
export async function getMerchantFulfillment(): Promise<PendingOrder[] | null> {
  const res = await http.get('/merchant/fulfillment');
  return res.data?.data ?? null;
}

export interface ReplenishRecord {
  id?: number;
  skuId: number;
  beforeQty: number;
  addQty: number;
  afterQty: number;
  triggerType: 'MANUAL' | 'AUTO';
  remark?: string;
  createTime?: string;
}

/** 最近补货记录（供应链补货中心） */
export async function getReplenishRecords(limit = 10): Promise<ReplenishRecord[] | null> {
  const res = await http.get('/merchant/supply/records?limit=' + limit);
  return res.data?.data ?? null;
}

/** 自动补货：低库存 SKU 补到目标库存（演示口径） */
export async function autoReplenish(threshold = 10, targetQty = 50): Promise<ReplenishRecord[] | null> {
  const res = await http.post('/merchant/supply/auto-replenish', { threshold, targetQty });
  return res.data?.data ?? null;
}

/** 手动补货：指定 SKU 增加库存（演示口径） */
export async function manualReplenish(skuId: number, addQty: number, remark?: string): Promise<ReplenishRecord | null> {
  const res = await http.post('/merchant/supply/replenish', { skuId, addQty, remark });
  return res.data?.data ?? null;
}

export interface FulfillmentActionRecord {
  id?: number;
  orderId: string;
  action: 'SPLIT' | 'DELAY' | 'OFF_SHELF' | 'REPLENISH';
  reason?: string;
  status: string;
  createTime?: string;
}

export const ACTION_LABELS: Record<string, string> = {
  SPLIT: '拆分发货', DELAY: '延期发货', OFF_SHELF: '下架停单', REPLENISH: '补货后发货',
}

/** 异常订单自动处理（SPLIT/DELAY/OFF_SHELF/REPLENISH，演示口径） */
export async function runFulfillmentAction(action: string, orderIds: string[], reason?: string): Promise<number | null> {
  const res = await http.post('/merchant/fulfillment/action', { action, orderIds, reason });
  return res.data?.data ?? null;
}

/** 最近异常订单处理记录 */
export async function getFulfillmentActions(limit = 10): Promise<FulfillmentActionRecord[] | null> {
  const res = await http.get('/merchant/fulfillment/actions?limit=' + limit);
  return res.data?.data ?? null;
}

/** 销量预测（7/30 天，演示口径：近 7 日日均×天数×(1+趋势)） */
export interface ForecastItem {
  skuId: string; productName: string; currentStock: number; last7Total: number
  avgDaily: number; trendPct: number; forecastDays: number; forecastQty: number
  suggestStock: number; hotLevel: string
}
export async function getMerchantForecast(days = 7): Promise<ForecastItem[] | null> {
  const res = await http.get('/merchant/forecast?days=' + days);
  return res.data?.data ?? null;
}

/** 智能选品（热度/库存/趋势，演示口径） */
export interface SelectionItem {
  skuId: string; productName: string; currentStock: number; last7Total: number
  trendPct: number; hotLevel: string; reason: string
}
export async function getMerchantSelection(): Promise<SelectionItem[] | null> {
  const res = await http.get('/merchant/selection');
  return res.data?.data ?? null;
}

/** 动态定价建议（趋势/库存/竞品，演示口径） */
export interface PricingItem {
  skuId: string; productName: string; currentPriceFen: number; competitorAvgFen: number
  inventoryLevel: string; trendPct: number; suggestPriceFen: number; action: string; reason: string
}
export async function getMerchantPricing(): Promise<PricingItem[] | null> {
  const res = await http.get('/merchant/pricing');
  return res.data?.data ?? null;
}

/** 营销方案建议（促销策略+触达渠道，演示口径） */
export interface MarketingPlanItem {
  skuId: string; productName: string; strategy: string; detail: string; reason: string; channels: string[]
}
export async function getMarketingPlan(): Promise<MarketingPlanItem[] | null> {
  const res = await http.get('/merchant/marketing-plan');
  return res.data?.data ?? null;
}

/** 评论管理：待回复评论（情感 + AI 建议话术，演示口径） */
export interface ReviewPendingItem {
  reviewId: string; spuId: string; userName: string; rating: number; content: string
  sentiment: 'NEGATIVE' | 'NEUTRAL' | 'POSITIVE'; aiSuggestion: string; createTime: string
}
export async function getReviewsPending(): Promise<ReviewPendingItem[] | null> {
  const res = await http.get('/merchant/reviews/pending');
  return res.data?.data ?? null;
}

/** 评论管理：提交回复 */
export async function postReviewReply(reviewId: string, content: string): Promise<boolean> {
  const res = await http.post('/merchant/reviews/reply', { reviewId, content });
  return !!res.data?.data?.replied;
}
