import http from './request';

export interface UserProfile {
  userId: string;
  phone: string;
  nickname: string;
  avatarUrl: string;
  gender: number;
  birthday?: string | null;
  level: number;
  point: number;
}

export interface AddressItem {
  addressId: string;
  receiverName: string;
  receiverPhone: string;
  province: string;
  city: string;
  district: string;
  detail: string;
  isDefault: number;
}

export interface AddressBody {
  receiverName: string;
  receiverPhone: string;
  province: string;
  city: string;
  district: string;
  detail: string;
}

export async function getProfile(): Promise<UserProfile | null> {
  const res = await http.get<{ code: number; data: UserProfile | null }>('/user/profile');
  return res.data.data;
}

export async function updateProfile(body: { nickname?: string; avatarUrl?: string; gender?: number; birthday?: string }): Promise<UserProfile | null> {
  const res = await http.put<{ code: number; data: UserProfile | null }>('/user/profile', body);
  return res.data.data;
}

// ── 地址 ──

export async function listAddresses(): Promise<AddressItem[]> {
  const res = await http.get<{ code: number; data: AddressItem[] }>('/address/list');
  return res.data.data ?? [];
}

export async function addAddress(body: AddressBody): Promise<AddressItem> {
  const res = await http.post<{ code: number; data: AddressItem }>('/address', body);
  return res.data.data;
}

export async function updateAddress(addressId: string, body: AddressBody): Promise<AddressItem> {
  const res = await http.put<{ code: number; data: AddressItem }>(`/address/${addressId}`, body);
  return res.data.data;
}

export async function deleteAddress(addressId: string): Promise<void> {
  await http.delete(`/address/${addressId}`);
}

export async function setDefaultAddress(addressId: string): Promise<void> {
  await http.put(`/address/${addressId}/default`, {});
}
