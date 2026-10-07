import http from './request';

export interface AftersaleOrder {
  id: number;
  aftersaleNo: string;
  orderNo: string;
  userId: number;
  type: string;
  reason: string;
  /** 退款金额（分） */
  amount: number;
  status: 'APPLYING' | 'SELLER_APPROVED' | 'REFUNDING' | 'REFUNDED' | 'REJECTED' | 'CANCELED';
  images: string;
  rejectReason: string;
  skuId: number | null;
  count: number | null;
  refundNo: string;
  applyAt: string;
  finishAt: string;
}

export interface ApplyBody {
  orderNo: string;
  type: string;
  reason?: string;
  amount: number;
  skuId?: number | null;
  count?: number | null;
  images?: string[];
}

export async function applyAftersale(body: ApplyBody): Promise<AftersaleOrder> {
  const res = await http.post<{ code: number; data: AftersaleOrder }>('/aftersale/apply', body);
  return res.data.data;
}

export async function getAftersaleMine(): Promise<AftersaleOrder[]> {
  const res = await http.get<{ code: number; data: AftersaleOrder[] }>('/aftersale/mine');
  return res.data.data ?? [];
}

export async function getAftersaleDetail(no: string): Promise<AftersaleOrder | null> {
  const res = await http.get<{ code: number; data: AftersaleOrder }>(`/aftersale/${no}`);
  return res.data.data ?? null;
}

export async function cancelAftersale(no: string): Promise<void> {
  await http.post(`/aftersale/${no}/cancel`, {});
}
