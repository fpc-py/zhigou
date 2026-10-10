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

// ── 收藏 / 浏览历史 / 画像 ──

export interface ProductTrackItem {
  id: string;
  spuId: string;
  skuId?: string;
  spuName: string;
  price?: number;
  imageUrl?: string;
  browseCount?: number;
  createTime?: string;
  lastBrowseTime?: string;
}

export interface ProductTrackBody {
  spuId: string;
  skuId?: string;
  spuName?: string;
  price?: number;
  imageUrl?: string;
}

export interface UserInsight {
  favoriteCount: number;
  browseCount: number;
  browseTotal: number;
  topCategories: string[];
  priceBand: string;
  recentBrowse: ProductTrackItem[];
  note: string;
}

export async function addFavorite(body: ProductTrackBody): Promise<ProductTrackItem | null> {
  const res = await http.post<{ code: number; data: ProductTrackItem | null }>('/user/favorite', body);
  return res.data.data;
}

export async function removeFavorite(spuId: string): Promise<void> {
  await http.delete(`/user/favorite/${spuId}`);
}

export async function listFavorites(page = 1, size = 10): Promise<{ records: ProductTrackItem[]; total: number }> {
  const res = await http.get<{ code: number; data: { records: ProductTrackItem[]; total: number } }>('/user/favorite', { params: { page, size } });
  return res.data.data ?? { records: [], total: 0 };
}

export async function favoriteIds(): Promise<string[]> {
  const res = await http.get<{ code: number; data: string[] }>('/user/favorite/ids');
  return res.data.data ?? [];
}

export async function recordBrowse(body: ProductTrackBody): Promise<void> {
  await http.post('/user/browse', body);
}

export async function recentBrowse(limit = 20): Promise<ProductTrackItem[]> {
  const res = await http.get<{ code: number; data: ProductTrackItem[] }>('/user/browse/recent', { params: { limit } });
  return res.data.data ?? [];
}

export async function getInsight(): Promise<UserInsight | null> {
  const res = await http.get<{ code: number; data: UserInsight | null }>('/user/insight');
  return res.data.data;
}
