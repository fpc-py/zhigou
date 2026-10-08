import http from './request';

export interface ProductItem {
  /** Snowflake ID，后端以字符串返回以避免 JS 精度丢失 */
  spuId: string;
  name: string;
  priceMin: number;
  priceMax: number;
  mainImage?: string;
  salesVolume?: number;
  subtitle?: string;
  /** 规格列表（加购取 skus[0].skuId） */
  skus?: Array<{ skuId: string; specName?: string; specValue?: string; price?: number; stock?: number; image?: string }>;
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
  personal?: {
    sceneText: string;
    sourceDesc: string;
    items: Array<{
      spuId: string;
      name: string;
      mainImage?: string;
      priceMin: number;
      score: number;
      avgRating?: number | null;
      reviewCount: number;
      reasons: string[];
      tags: string[];
    }>;
  };
}

export async function getHomeFeed(): Promise<HomeFeedData> {
  const res = await http.get<{ code: number; data: HomeFeedData }>('/home/feed');
  return res.data.data;
}