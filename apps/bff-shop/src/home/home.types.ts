/** /home/feed 响应体类型 */

export interface ProductItem {
  spuId: number;
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

export interface HomeFeedResponse {
  banner: string[];           // 营销 banner URL 列表
  recommend: RecommendItem[]; // RAG 推荐商品
  products: ProductItem[];    // 全部商品列表
  personal?: PersonalRecommend; // 个性化推荐（画像驱动，P1 五批）
}

/** 个性化推荐（product-service /recommend） */
export interface PersonalRecommend {
  sceneText: string;
  sourceDesc: string;
  items: PersonalRecommendItem[];
}

export interface PersonalRecommendItem {
  spuId: string;
  name: string;
  mainImage?: string;
  priceMin: number;
  score: number;
  avgRating?: number | null;
  reviewCount: number;
  reasons: string[];
  tags: string[];
}