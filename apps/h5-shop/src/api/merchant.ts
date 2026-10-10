/** 商家经营 API（BFF 透传 order-service 经营概览；单商家市场 = 平台聚合演示口径） */
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
  const res = await http.get<{ code: number; data: MerchantOverview | null }>('/merchant/overview');
  return res.data.data;
}

export interface StockLow { skuId: number; available: number }
export interface ReviewNegative { spuId: string; rating: number; content: string; userName?: string; createTime?: string }
export interface MerchantWarnings {
  lowStock: StockLow[];
  negative: ReviewNegative[];
}

/** 商家经营预警（低库存 + 差评） */
export async function getMerchantWarnings(): Promise<MerchantWarnings | null> {
  const res = await http.get<{ code: number; data: MerchantWarnings | null }>('/merchant/warnings');
  return res.data.data;
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
  const res = await http.get<{ code: number; data: PendingOrder[] | null }>('/merchant/fulfillment');
  return res.data.data;
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
  const res = await http.get<{ code: number; data: ReplenishRecord[] | null }>('/merchant/supply/records?limit=' + limit);
  return res.data.data;
}

/** 自动补货：低库存 SKU 补到目标库存（演示口径） */
export async function autoReplenish(threshold = 10, targetQty = 50): Promise<ReplenishRecord[] | null> {
  const res = await http.post<{ code: number; data: ReplenishRecord[] | null }>('/merchant/supply/auto-replenish', {
    threshold,
    targetQty,
  });
  return res.data.data;
}

/** 手动补货：指定 SKU 增加库存（演示口径） */
export async function manualReplenish(skuId: number, addQty: number, remark?: string): Promise<ReplenishRecord | null> {
  const res = await http.post<{ code: number; data: ReplenishRecord | null }>('/merchant/supply/replenish', {
    skuId,
    addQty,
    remark,
  });
  return res.data.data;
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
  const res = await http.post<{ code: number; data: number | null }>('/merchant/fulfillment/action', {
    action,
    orderIds,
    reason,
  });
  return res.data.data;
}

/** 最近异常订单处理记录 */
export async function getFulfillmentActions(limit = 10): Promise<FulfillmentActionRecord[] | null> {
  const res = await http.get<{ code: number; data: FulfillmentActionRecord[] | null }>('/merchant/fulfillment/actions?limit=' + limit);
  return res.data.data;
}
