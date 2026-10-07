/** /order/* 响应体类型（透传 order-service） */

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

export interface CreateOrderBody {
  requestId: string;
  skuItems: Array<{ skuId: string; count: number }>;
  couponId?: number | null;
}
