import http from './request';

export interface ProductDetailData {
  product: Record<string, any> | null;
  aiReason: string | null;
}

export async function getProductDetail(spuId: string): Promise<ProductDetailData> {
  const res = await http.get<{ code: number; data: ProductDetailData }>(`/product/${spuId}/detail`);
  return res.data.data;
}

export interface ProductPageItem {
  spuId: string;
  name: string;
  subtitle?: string;
  mainImage?: string;
  priceMin: number;
  priceMax: number;
  salesVolume?: number;
  skus?: Array<{ skuId: string; specName?: string; specValue?: string; price?: number; stock?: number; image?: string }>;
}

export interface ProductPageResult {
  records: ProductPageItem[];
  total: number;
}

/** 商品分页（关键词/分类/品牌） */
export async function getProductPage(params: { keyword?: string; pageNum?: number; pageSize?: number; categoryId?: number }): Promise<ProductPageResult> {
  const res = await http.get<{ code: number; data: ProductPageResult }>('/product/page', { params });
  return res.data.data ?? { records: [], total: 0 };
}