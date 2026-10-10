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

  // 业务服务（单次下游调用超时后降级；原值 500ms 对冷启动/首次查询过短，易误判超时）
  userService:      { url: 'http://localhost:8081', timeout: 2_000 },
  fileService:      { url: 'http://localhost:8082', timeout: 2_000 },
  productService:   { url: 'http://localhost:8083', timeout: 2_000 },
  cartService:      { url: 'http://localhost:8084', timeout: 2_000 },
  orderService:     { url: 'http://localhost:8085', timeout: 2_000 },
  inventorySvc:     { url: 'http://localhost:8086', timeout: 2_000 },
  paymentService:   { url: 'http://localhost:8087', timeout: 2_000 },
  marketingSvc:     { url: 'http://localhost:8088', timeout: 2_000 },
  logisticsSvc:     { url: 'http://localhost:8089', timeout: 2_000 },
  aftersaleSvc:     { url: 'http://localhost:8090', timeout: 2_000 },
  communitySvc:     { url: 'http://localhost:8091', timeout: 2_000 },
  lifeSvc:           { url: 'http://localhost:8092', timeout: 2_000 },
  closetSvc:         { url: 'http://localhost:8093', timeout: 2_000 },

  // AI 服务（SSE 需要长连接）
  aiOrchestrator: { url: 'http://localhost:8000', timeout: 30_000 },

  // RAG 摘要（商品详情 AI 理由）：300ms 快速失败，不拖慢详情接口
  aiRag: { url: 'http://localhost:8000', timeout: 300 },
};

export const SERVICE_PATHS = {
  productPage:       '/product/page',
  productDetail:     (spuId: string) => `/product/${spuId}`,
  productSku:        (skuId: string) => `/product/sku/${skuId}`,
  productRecommend:  '/recommend',
  priceCompare:      '/price/compare',
  userCoupons:       '/coupon/mine',
  discountCalculate: '/discount/calculate',
  groupBuyActivities: '/group-buy/activities',
  groupBuyOpen:       '/group-buy/open',
  groupBuyJoin:       '/group-buy/join',
  groupBuyMine:       '/group-buy/mine',
  groupBuyDetail:     (id: string) => `/group-buy/group/${id}`,
  freightCalculate:  '/freight/calculate',
  ragRetrieve:       '/api/v1/rag/retrieve',
  chatSse:           '/api/v1/chat/sse',
  authSendSms:       '/auth/send-sms-code',
  authLogin:         '/auth/login',
  authRefresh:       '/auth/refresh',
  cartMine:          '/cart/mine',
  cartAdd:           '/cart/add',
  cartUpdate:        '/cart/update',
  cartRemove:        (skuId: string) => `/cart/${skuId}`,
  cartClear:         '/cart/clear',
  orderCreate:       '/order/create',
  orderCancel:       (id: string) => `/order/${id}/cancel`,
  orderDetail:       (id: string) => `/order/${id}`,
  orderMine:         '/order/mine',
  paymentCreate:     '/payment/create',
  paymentMockPay:    '/payment/sandbox/mock-pay',
  userProfile:       '/user/profile',
  addressList:       '/address/list',
  addressRoot:       '/address',
  addressDefault:    (id: string) => `/address/${id}/default`,
  communityNotePublish: '/community/note',
  communityNotePage:   '/community/note/page',
  communityNoteDetail: (id: string) => `/community/note/${id}`,
  communityNoteLike:   (id: string) => `/community/note/${id}/like`,
  communityNoteFavorite: (id: string) => `/community/note/${id}/favorite`,
  communityComment:    '/community/comment',
  communityMine:       '/community/mine',
  communityVideoPage:   '/community/video/page',
  communityVideoDetail: (id: string) => `/community/video/${id}`,
  communityVideoLike:   (id: string) => `/community/video/${id}/like`,
  communityVideoFavorite: (id: string) => `/community/video/${id}/favorite`,
  communityVideoPublish: '/community/video',
  communityLiveList:    '/community/live/list',
  communityLiveDetail:  (id: string) => `/community/live/${id}`,
  communityAiWriter:   '/api/v1/community/writer',
  lifePoiPage:        '/life/poi/page',
  lifePoiDetail:      (id: string) => `/life/poi/${id}`,
  lifeSkuPage:        '/life/sku/page',
  lifeAppointment:    '/life/appointment',
  lifeAppointmentMine:'/life/appointment/mine',
  lifeAppointmentCancel: (id: string) => `/life/appointment/${id}/cancel`,
  closetItems:        '/closet/items',
  closetItem:         '/closet/item',
  closetItemWear:     (id: string) => `/closet/item/${id}/wear`,
  closetItemDelete:   (id: string) => `/closet/item/${id}`,
  closetOutfitRecommend: '/closet/outfit/recommend',
  closetHomeList:     '/closet/home/list',
  closetHomeItem:     '/closet/home/item',
  closetHomeReplenish: '/closet/home/replenish',
};