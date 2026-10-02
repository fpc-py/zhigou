import http from './request';

export interface LoginResponse {
  accessToken: string;
  refreshToken: string;
  expiresIn: number;
}

/** 发送短信验证码 */
export async function sendSmsCode(phone: string): Promise<void> {
  await http.post('/auth/send-sms-code', { phone });
}

/** 手机号+验证码登录 */
export async function login(phone: string, code: string): Promise<LoginResponse> {
  const res = await http.post<{ code: number; data: LoginResponse }>('/auth/login', { phone, code });
  return res.data.data;
}

/** 刷新 token */
export async function refreshToken(refreshToken: string): Promise<LoginResponse> {
  const res = await http.post<{ code: number; data: LoginResponse }>('/auth/refresh', { refreshToken });
  return res.data.data;
}