import http from './request';

export interface PaymentCreateResult {
  paymentNo: string;
  qrUrl: string;
}

/** 沙箱验签密钥（仅演示环境；生产替换微信/支付宝 SDK 后删除） */
const SANDBOX_SECRET = 'sandbox-secret-key';

/** SHA-256 hex（Web Crypto API，localhost/HTTPS 安全上下文可用） */
async function sha256Hex(text: string): Promise<string> {
  const data = new TextEncoder().encode(text);
  const buf = await crypto.subtle.digest('SHA-256', data);
  return Array.from(new Uint8Array(buf))
    .map((b) => b.toString(16).padStart(2, '0'))
    .join('');
}

/** 创建沙箱支付单 */
export async function createPayment(orderNo: string, amount: number): Promise<PaymentCreateResult> {
  const res = await http.post<{ code: number; data: PaymentCreateResult }>('/payment/create', {
    orderNo,
    amount,
  });
  return res.data.data;
}

/** 沙箱模拟支付成功（sign = sha256(paymentNo + secret)，与 payment-service 验签一致） */
export async function mockPay(paymentNo: string): Promise<void> {
  const sign = await sha256Hex(paymentNo + SANDBOX_SECRET);
  await http.post('/payment/sandbox/mock-pay', { paymentNo, sign });
}
