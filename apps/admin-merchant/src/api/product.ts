import http from './request';

export interface SpuItem {
  /** 雪花 ID 超出 JS 安全整数（2^53），全程字符串处理防精度丢失 */
  spuId: string;
  name: string;
  priceMin: number;
  priceMax: number;
  salesVolume: number;
  status: number;
  mainImage?: string;
  categoryId?: string;
  brandId?: string;
  subtitle?: string;
  description?: string;
  skus?: SkuItem[];
}

export interface SkuItem {
  skuId?: string;
  specName: string;
  specValue: string;
  price: number;
  stock: number;
  image?: string;
}

export interface SpuListParams {
  pageNum?: number;
  pageSize?: number;
  keyword?: string;
  categoryId?: number;
  brandId?: number;
}

export interface PageResult<T> {
  records: T[];
  total: number;
  current: number;
  size: number;
}

export async function getSpuList(params: SpuListParams): Promise<PageResult<SpuItem>> {
  const res = await http.get('/product/page', { params });
  return res.data.data;
}

export async function getSpuDetail(spuId: string): Promise<SpuItem> {
  const res = await http.get(`/product/${spuId}/detail`);
  return res.data.data.product;
}

export async function createSpu(data: any): Promise<string> {
  const res = await http.post('/product/spu', data);
  return res.data.data.spuId;
}

export async function updateSpu(spuId: string, data: any): Promise<void> {
  await http.put(`/product/spu/${spuId}`, data);
}

export async function offShelf(spuId: string): Promise<void> {
  await http.delete(`/product/spu/${spuId}`);
}