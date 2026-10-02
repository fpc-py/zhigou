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
}