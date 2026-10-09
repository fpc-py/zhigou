import http from './request';

/** 跨平台报价（P1 六批：京东/天猫/拼多多渠道，本地模拟源） */
export interface CompareOffer {
  source: string;
  price: number;      // 售价（分）
  shippingFee: number; // 运费（分）
  totalPrice: number;  // 总价（分）
  deliveryDays: number;
  promoText: string;
  isBest: boolean;
}

export interface PriceCompareItem {
  skuId: string;
  spuId: string;
  skuName: string;
  offers: CompareOffer[];
  bestSource: string;
  bestTotalPrice: number;
  suggestion: string;
}

/** 跨平台比价：多 SKU 聚合各渠道报价 + 最优购买方案 */
export async function comparePrices(skuIds: number[]): Promise<PriceCompareItem[]> {
  const res = await http.post<{ code: number; data: PriceCompareItem[] }>('/price/compare', skuIds);
  return res.data.data ?? [];
}
