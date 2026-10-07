/** /payment/* 响应体类型（透传 payment-service 沙箱支付） */

export interface PaymentCreateBody {
  orderNo: string;
  amount: number;
}

export interface PaymentCreateResult {
  paymentNo: string;
  qrUrl: string;
}

export interface MockPayBody {
  paymentNo: string;
  sign?: string;
}
