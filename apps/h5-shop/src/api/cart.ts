import http from './request';

/** 购物车条目（BFF 聚合：含商品信息） */
export interface CartItem {
  skuId: string;
  count: number;
  selected: boolean;
  priceAtAdd: number;
  spuId?: string;
  name?: string;
  specName?: string;
  specValue?: string;
  price?: number;
  stock?: number;
  image?: string;
}

export async function getCartMine(): Promise<CartItem[]> {
  const res = await http.get<{ code: number; data: CartItem[] }>('/cart/mine');
  return res.data.data ?? [];
}

export async function addCart(skuId: string, count = 1): Promise<void> {
  await http.post('/cart/add', { skuId, count });
}

export async function updateCart(skuId: string, patch: { count?: number; selected?: boolean }): Promise<void> {
  await http.post('/cart/update', { skuId, ...patch });
}

export async function removeCart(skuId: string): Promise<void> {
  await http.delete(`/cart/${skuId}`);
}

export async function clearCart(): Promise<void> {
  await http.post('/cart/clear', {});
}
