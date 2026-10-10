/** 会员钱包 API（BFF 透传 wallet-service） */
import http from './request';

export interface WalletAccount {
  id: string;
  userId: string;
  balanceFen: number;
  totalRechargeFen: number;
  totalConsumeFen: number;
  points: number;
  totalPoints: number;
  memberLevel: string; // FREE/ADVANCED/FLAGSHIP
  status: number;
}

export interface WalletTransaction {
  id: string;
  userId: string;
  type: string; // RECHARGE/CONSUME/REFUND
  amountFen: number;
  balanceAfterFen: number;
  bizNo: string;
  remark?: string;
  createdAt: string;
}

export interface MemberLevelInfo {
  level: string;
  title: string;
  benefits: string[];
  note: string;
}

/** 我的钱包账户 */
export async function getWalletAccount(): Promise<WalletAccount | null> {
  const res = await http.get<{ code: number; data: WalletAccount | null }>('/wallet/account');
  return res.data.data;
}

/** 沙箱充值 */
export async function rechargeWallet(body: { amountFen: number; remark?: string }): Promise<WalletTransaction | null> {
  const res = await http.post<{ code: number; data: WalletTransaction | null }>('/wallet/recharge', body);
  return res.data.data;
}

/** 流水分页 */
export async function getWalletTransactions(pageNum = 1, pageSize = 20): Promise<WalletTransaction[]> {
  const res = await http.get<{ code: number; data: WalletTransaction[] }>('/wallet/transactions', {
    params: { pageNum, pageSize },
  });
  return res.data.data ?? [];
}

/** 会员等级与权益 */
export async function getMemberLevel(): Promise<MemberLevelInfo | null> {
  const res = await http.get<{ code: number; data: MemberLevelInfo | null }>('/wallet/level');
  return res.data.data;
}

export interface SubscriptionInfo {
  level: string; // FREE/ADVANCED/FLAGSHIP
  active: boolean;
  expireAt?: string;
  benefits: string[];
  note: string;
}

/** 当前订阅状态 */
export async function getSubscription(): Promise<SubscriptionInfo | null> {
  const res = await http.get<{ code: number; data: SubscriptionInfo | null }>('/wallet/subscription');
  return res.data.data;
}

/** 开通/续费会员（沙箱扣余额） */
export async function subscribeMember(level: string): Promise<SubscriptionInfo | null> {
  const res = await http.post<{ code: number; data: SubscriptionInfo | null }>('/wallet/subscribe', { level });
  return res.data.data;
}
