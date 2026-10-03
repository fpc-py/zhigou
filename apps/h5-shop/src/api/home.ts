import http from './request';

export interface ProductItem {
  /** Snowflake ID，后端以字符串返回以避免 JS 精度丢失 */
  spuId: string;
  name: string;
  priceMin: number;
  priceMax: number;
  mainImage?: string;
  salesVolume?: number;
}

export interface RecommendItem {
  spu_id: number;
  text: string;
  score: number;
}

export interface HomeFeedData {
  banner: string[];
  recommend: RecommendItem[];
  products: ProductItem[];
}

export async function getHomeFeed(): Promise<HomeFeedData> {
  const res = await http.get<{ code: number; data: HomeFeedData }>('/home/feed');
  return res.data.data;
}