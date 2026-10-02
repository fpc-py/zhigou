import http from './request';

export interface ProductDetailData {
  product: Record<string, any> | null;
  aiReason: string | null;
}

export async function getProductDetail(spuId: string): Promise<ProductDetailData> {
  const res = await http.get<{ code: number; data: ProductDetailData }>(`/product/${spuId}/detail`);
  return res.data.data;
}