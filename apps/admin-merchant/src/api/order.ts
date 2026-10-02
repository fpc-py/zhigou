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

/** 获取订单详情（后端暂无列表接口，前端 mock 列表） */
export async function getOrderDetail(orderId: string): Promise<OrderItem> {
  const res = await http.get(`/order/order/${orderId}`);
  return res.data.data;
}