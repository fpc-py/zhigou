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

export interface DelayAlert {
  shipmentNo: string;
  orderNo: string;
  status: string;
  carrier?: string;
  latestNode?: string;
  latestTime?: string | null;
  stagnant: boolean;
  hint?: string;
}

export interface DispatchResult {
  id?: number;
  shipmentNo: string;
  action: string;
  reason?: string;
  status: string;
}

export const DISPATCH_LABELS: Record<string, string> = {
  URGE: '催件', REDELIVER: '重新派送', SELF_PICKUP: '改自提', CHANGE_ADDRESS: '改址', RETURN: '退换货',
}

/** 物流延误预警扫描：在途运单疑似停滞清单（用户侧物流管家） */
export async function getDelayAlerts(stagnantHours = 48): Promise<DelayAlert[] | null> {
  const res = await http.get<{ code: number; data: DelayAlert[] | null }>('/freight/delay-alerts?stagnantHours=' + stagnantHours);
  return res.data.data;
}

/** 一键调度：催件/重派/自提/改址/退换货（演示口径） */
export async function runDispatch(shipmentNo: string, action: string, reason?: string): Promise<DispatchResult | null> {
  const res = await http.post<{ code: number; data: DispatchResult | null }>('/freight/dispatch', { shipmentNo, action, reason });
  return res.data.data;
}
