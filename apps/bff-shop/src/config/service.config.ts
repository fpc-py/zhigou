/**
 * 智购 BFF — 下游微服务地址配置
 * 所有 URL 和超时集中管理，后续可接 Nacos/ConfigMap
 */
export interface ServiceEntry {
  url: string;
  timeout: number; // 毫秒
}

export const SERVICES: Record<string, ServiceEntry> = {
  // 鉴权（登录/刷新不需要 500ms 限制）
  authCenter: { url: 'http://localhost:8080', timeout: 5_000 },

  // 业务服务（每个下游 500ms，超过就降级）
  userService:    { url: 'http://localhost:8081', timeout: 500 },
  productService: { url: 'http://localhost:8083', timeout: 500 },
  cartService:    { url: 'http://localhost:8084', timeout: 500 },
  inventorySvc:   { url: 'http://localhost:8086', timeout: 500 },
  marketingSvc:   { url: 'http://localhost:8088', timeout: 500 },

  // AI 服务（SSE 需要长连接）
  aiOrchestrator: { url: 'http://localhost:8000', timeout: 30_000 },
};

export const SERVICE_PATHS = {
  productPage:       '/product/page',
  productDetail:     (spuId: string) => `/product/${spuId}`,
  userCoupons:       '/coupon/mine',
  ragRetrieve:       '/api/v1/rag/retrieve',
  chatSse:           '/api/v1/chat/sse',
  authSendSms:       '/auth/send-sms-code',
  authLogin:         '/auth/login',
  authRefresh:       '/auth/refresh',
};