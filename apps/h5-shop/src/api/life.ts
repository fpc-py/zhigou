/** 本地生活 API（BFF 透传 life-service） */
import http from './request';

export interface PoiStore {
  id: string;
  name: string;
  category: string;
  address: string;
  distanceM: number;
  avgPriceFen: number;
  rating: number;
  coverUrl?: string;
  hours?: string;
  tags?: string;
  businessStatus: number;
}

export interface LifeSku {
  id: string;
  storeId: string;
  name: string;
  category: string;
  priceFen: number;
  unit: string;
  stock: number;
  coverUrl?: string;
  description?: string;
}

export interface LifeAppointment {
  id: string;
  userId: string;
  storeId: string;
  skuId?: string;
  appointmentTime: string;
  remark?: string;
  status: number; // 0=待确认 1=已确认 2=已完成 3=已取消
  createdAt: string;
}

/** 门店/商圈分页 */
export async function getStores(category?: string, pageNum = 1, pageSize = 20): Promise<PoiStore[]> {
  const params: Record<string, string | number> = { pageNum, pageSize };
  if (category) params.category = category;
  const res = await http.get<{ code: number; data: PoiStore[] }>('/life/poi/page', { params });
  return res.data.data ?? [];
}

/** 门店详情（含服务 SKU） */
export async function getStoreDetail(id: string): Promise<{ store: PoiStore; skus: LifeSku[] } | null> {
  const res = await http.get<{ code: number; data: { store: PoiStore; skus: LifeSku[] } | null }>(`/life/poi/${id}`);
  return res.data.data;
}

/** 服务 SKU 分页 */
export async function getSkus(category?: string, pageNum = 1, pageSize = 20): Promise<LifeSku[]> {
  const params: Record<string, string | number> = { pageNum, pageSize };
  if (category) params.category = category;
  const res = await http.get<{ code: number; data: LifeSku[] }>('/life/sku/page', { params });
  return res.data.data ?? [];
}

/** 创建到店/服务预约 */
export async function createAppointment(body: {
  storeId: string;
  skuId?: string;
  appointmentTime: string;
  remark?: string;
}): Promise<LifeAppointment | null> {
  const res = await http.post<{ code: number; data: LifeAppointment | null }>('/life/appointment', body);
  return res.data.data;
}

/** 我的预约 */
export async function getMyAppointments(): Promise<LifeAppointment[]> {
  const res = await http.get<{ code: number; data: LifeAppointment[] }>('/life/appointment/mine');
  return res.data.data ?? [];
}

/** 取消预约 */
export async function cancelAppointment(id: string): Promise<LifeAppointment | null> {
  const res = await http.post<{ code: number; data: LifeAppointment | null }>(`/life/appointment/${id}/cancel`, {});
  return res.data.data;
}
