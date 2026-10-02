/** /product/:spuId/detail 响应体类型 */

export interface ProductDetailResponse {
  product: Record<string, any> | null;
  aiReason: string | null;
}