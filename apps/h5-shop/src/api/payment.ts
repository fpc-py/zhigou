import http from './request';

export interface PaymentCreateResult {
  paymentNo: string;
  qrUrl: string;
}

/** 创建沙箱支付单 */
export async function createPayment(orderNo: string, amount: number): Promise<PaymentCreateResult> {
  const res = await http.post<{ code: number; data: PaymentCreateResult }>('/payment/create', {
    orderNo,
    amount,
  });
  return res.data.data;
}

/** 沙箱模拟支付成功 */
export async function mockPay(paymentNo: string): Promise<void> {
  await http.post('/payment/sandbox/mock-pay', { paymentNo, sign: 'sandbox-mock' });
}
