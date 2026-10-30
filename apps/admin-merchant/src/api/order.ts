import http from './request';

export interface OrderItem {
  orderId: number;
  orderStatus: string;
  totalAmount: number;
  payAmount: number;
  items?: OrderSkuItem[];
}

export interface OrderSkuItem {
  skuId: number;
  skuName: string;
  price: number;
  count: number;
}

/** 商家订单列表（多商家隔离：BFF /order/merchant/list 按归属商家过滤，分页） */
export async function listMerchantOrders(status?: string, pageNum = 1, pageSize = 20): Promise<{ records: OrderItem[]; total: number }> {
  const params = new URLSearchParams({ pageNum: String(pageNum), pageSize: String(pageSize) });
  if (status) params.set('status', status);
  const res = await http.get(`/order/merchant/list?${params.toString()}`);
  return res.data?.data ?? { records: [], total: 0 };
}

/** 商家订单详情（多商家隔离：归属校验，非本商家订单 403） */
export async function getMerchantOrderDetail(orderId: string): Promise<OrderItem> {
  const res = await http.get(`/order/merchant/${orderId}`);
  return res.data.data;
}