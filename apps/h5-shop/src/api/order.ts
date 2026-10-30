import http from './request';

export interface OrderItem {
  skuId: string;
  skuName: string;
  price: number;
  count: number;
}

export interface OrderDetail {
  orderId: string;
  userId: string;
  orderStatus: string;
  totalAmount: number;
  payAmount: number;
  items: OrderItem[];
}

/** 提交订单（requestId 幂等；seckillActivityId 非空=秒杀单：营销侧预扣校验 + 秒杀价计价，不可叠加优惠券） */
export async function createOrder(body: {
  requestId: string;
  skuItems: Array<{ skuId: string; count: number }>;
  couponId?: number | null;
  seckillActivityId?: number | string;
}): Promise<OrderDetail> {
  const res = await http.post<{ code: number; data: OrderDetail }>('/order/create', body);
  return res.data.data;
}

export async function getOrderMine(): Promise<OrderDetail[]> {
  const res = await http.get<{ code: number; data: OrderDetail[] }>('/order/mine');
  return res.data.data ?? [];
}

export async function getOrderDetail(orderId: string): Promise<OrderDetail> {
  const res = await http.get<{ code: number; data: OrderDetail }>(`/order/${orderId}`);
  return res.data.data;
}

export async function cancelOrder(orderId: string): Promise<void> {
  await http.post(`/order/${orderId}/cancel`, {});
}
