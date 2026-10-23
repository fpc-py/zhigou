import http from './request';

export interface AftersaleItem {
  id: number;
  aftersaleNo: string;
  orderNo: string;
  userId: number;
  type: string;
  reason: string;
  /** 退款金额（分） */
  amount: number;
  status: 'APPLYING' | 'SELLER_APPROVED' | 'REFUNDING' | 'REFUNDED' | 'REJECTED' | 'CANCELED';
  rejectReason?: string;
  skuId?: number | null;
  count?: number | null;
  applyAt?: string;
  finishAt?: string;
}

/** 商家售后工作台：待处理列表 + 全状态统计（status 可选，默认 APPLYING） */
export async function getMerchantPending(status?: string) {
  const q = status ? `?status=${encodeURIComponent(status)}` : '';
  const res = await http.get(`/aftersale/merchant/pending${q}`);
  return res.data?.data as { list: AftersaleItem[]; counts: Record<string, number>; status: string } | null;
}

/** 售后详情 */
export async function getAftersaleDetail(no: string): Promise<AftersaleItem> {
  const res = await http.get(`/aftersale/${no}`);
  return res.data.data;
}

/** 商家同意退款 */
export async function approveAftersale(no: string): Promise<void> {
  await http.post(`/aftersale/${no}/approve`);
}

/** 商家拒绝售后（需理由） */
export async function rejectAftersale(no: string, reason: string): Promise<void> {
  await http.post(`/aftersale/${no}/reject`, { reason });
}
