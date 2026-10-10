/** 智能衣橱 + 家居管理 API（BFF 透传 closet-service） */
import http from './request';

export interface ClosetItem {
  id: string;
  userId: string;
  name: string;
  category: string; // 上装/下装/外套/鞋履/配饰
  season: string; // 春/夏/秋/冬/四季
  color?: string;
  imageUrl?: string;
  tags?: string;
  wearCount: number;
  lastWornAt?: string;
  status: number;
  createdAt: string;
}

export interface HomeAsset {
  id: string;
  userId: string;
  name: string;
  category: string; // 食品/日用品/家电/清洁
  quantity: number;
  unit: string;
  expireAt?: string;
  replenishAlert: number;
  createdAt: string;
}

export interface OutfitRecommend {
  occasion: string;
  items: ClosetItem[];
  note: string;
}

/** 我的衣物 */
export async function getClosetItems(category?: string, season?: string): Promise<ClosetItem[]> {
  const params: Record<string, string> = {};
  if (category) params.category = category;
  if (season) params.season = season;
  const res = await http.get<{ code: number; data: ClosetItem[] }>('/closet/items', { params });
  return res.data.data ?? [];
}

/** 添加衣物 */
export async function addClosetItem(body: {
  name: string;
  category: string;
  season: string;
  color?: string;
  tags?: string;
}): Promise<ClosetItem | null> {
  const res = await http.post<{ code: number; data: ClosetItem | null }>('/closet/item', body);
  return res.data.data;
}

/** 穿着打卡 */
export async function wearClosetItem(id: string): Promise<ClosetItem | null> {
  const res = await http.post<{ code: number; data: ClosetItem | null }>(`/closet/item/${id}/wear`, {});
  return res.data.data;
}

/** 删除衣物 */
export async function deleteClosetItem(id: string): Promise<ClosetItem | null> {
  const res = await http.delete<{ code: number; data: ClosetItem | null }>(`/closet/item/${id}`);
  return res.data.data;
}

/** 穿搭推荐（规则引擎） */
export async function recommendOutfit(occasion?: string): Promise<OutfitRecommend | null> {
  const params: Record<string, string> = {};
  if (occasion) params.occasion = occasion;
  const res = await http.get<{ code: number; data: OutfitRecommend | null }>('/closet/outfit/recommend', { params });
  return res.data.data;
}

/** 家居盘点 */
export async function getHomeAssets(category?: string): Promise<HomeAsset[]> {
  const params: Record<string, string> = {};
  if (category) params.category = category;
  const res = await http.get<{ code: number; data: HomeAsset[] }>('/closet/home/list', { params });
  return res.data.data ?? [];
}

/** 添加家居物品 */
export async function addHomeAsset(body: {
  name: string;
  category: string;
  quantity: number;
  unit: string;
  expireAt?: string;
}): Promise<HomeAsset | null> {
  const res = await http.post<{ code: number; data: HomeAsset | null }>('/closet/home/item', body);
  return res.data.data;
}

/** 补货清单 */
export async function getReplenishList(): Promise<HomeAsset[]> {
  const res = await http.get<{ code: number; data: HomeAsset[] }>('/closet/home/replenish');
  return res.data.data ?? [];
}
