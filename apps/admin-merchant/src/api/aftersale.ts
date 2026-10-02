import http from './request';

export interface AftersaleItem {
  aftersaleNo: string;
  orderNo: string;
  userId: number;
  type: string;
  reason: string;
  amount: number;
  status: string;
  rejectReason?: string;
  applyAt?: string;
}

/** 售后详情 */
export async function getAftersaleDetail(no: string): Promise<AftersaleItem> {
  const res = await http.get(`/aftersale/aftersale/${no}`);
  return res.data.data;
}

/** 同意退款 */
export async function approveRefund(no: string): Promise<void> {
  await http.post(`/aftersale/aftersale/${no}/approve`);
}

/** 拒绝退款 */
export async function rejectRefund(no: string, reason: string): Promise<void> {
  await http.post(`/aftersale/aftersale/${no}/reject`, { reason });
}