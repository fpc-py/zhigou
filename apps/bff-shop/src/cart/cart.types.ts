/** /cart/* 响应体类型（BFF 聚合：购物车条目 + 商品信息） */

export interface CartItem {
  /** SKU ID（Snowflake，字符串避免精度丢失） */
  skuId: string;
  count: number;
  selected: boolean;
  priceAtAdd: number;
  /** 以下为 BFF 聚合商品信息 */
  spuId?: string;
  name?: string;
  specName?: string;
  specValue?: string;
  price?: number;
  stock?: number;
  image?: string;
}

export interface CartAddBody {
  skuId: string;
  count?: number;
}

export interface CartUpdateBody {
  skuId: string;
  count?: number;
  selected?: boolean;
}
