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

/** 商家同意退款（售后单 -> SELLER_APPROVED） */
export async function approveAftersale(no: string): Promise<void> {
  await http.post(`/aftersale/${no}/approve`, {});
}

/** 商家拒绝售后（需理由） */
export async function rejectAftersale(no: string, reason: string): Promise<void> {
  await http.post(`/aftersale/${no}/reject`, { reason });
}


/** 商家售后工作台：待处理列表 + 全状态统计（商家中心；status 可选，默认 APPLYING） */
export async function getMerchantPending(status?: string) {
  const q = status ? `?status=${encodeURIComponent(status)}` : '';
  const res = await http.get<{
    code: number;
    data: { list: AftersaleOrder[]; counts: Record<string, number>; status: string };
  }>(`/aftersale/merchant/pending${q}`);
  return res.data.data;
}

/** 质保提醒：30 天内到期 / 已过期 / 正常（售后助手） */
export async function getWarrantyAlerts(days = 30) {
  const res = await http.get<{ code: number; data: any[] | null }>('/aftersale/warranty/alerts?days=' + days);
  return res.data.data;
}

/** 创建维修预约（演示口径 PENDING，正式版需商家确认排期） */
export async function createRepairAppointment(body: Record<string, any>) {
  const res = await http.post<{ code: number; data: any | null }>('/aftersale/repair/appointment', body);
  return res.data.data;
}

/** 我的维修预约 */
export async function getRepairAppointments() {
  const res = await http.get<{ code: number; data: any[] | null }>('/aftersale/repair/appointments');
  return res.data.data;
}
