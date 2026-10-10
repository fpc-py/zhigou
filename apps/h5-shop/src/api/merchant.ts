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
