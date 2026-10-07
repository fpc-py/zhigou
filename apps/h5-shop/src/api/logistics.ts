import http from './request';

export interface FreightResult {
  freightFee: number;
  freeShipping: boolean;
  reason: string;
}

/** 运费试算（totalAmount 单位为分） */
export async function calculateFreight(totalAmount: number, weightG = 0): Promise<FreightResult | null> {
  const res = await http.post<{ code: number; data: FreightResult | null }>('/freight/calculate', {
    totalAmount,
    weightG,
  });
  return res.data.data;
}
