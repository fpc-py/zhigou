import http from './request';

export interface SpuItem {
  spuId: number;
  name: string;
  priceMin: number;
  priceMax: number;
  salesVolume: number;
  status: number;
  mainImage?: string;
  categoryId?: number;
  brandId?: number;
  subtitle?: string;
  description?: string;
  skus?: SkuItem[];
}

export interface SkuItem {
  skuId?: number;
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
  const res = await http.get('/product/product/page', { params });
  return res.data.data;
}

export async function getSpuDetail(spuId: number): Promise<SpuItem> {
  const res = await http.get(`/product/product/${spuId}`);
  return res.data.data;
}

export async function createSpu(data: any): Promise<number> {
  const res = await http.post('/product/product/spu', data);
  return res.data.data.spuId;
}

export async function updateSpu(spuId: number, data: any): Promise<void> {
  await http.put(`/product/product/spu/${spuId}`, data);
}

export async function offShelf(spuId: number): Promise<void> {
  await http.delete(`/product/product/spu/${spuId}`);
}