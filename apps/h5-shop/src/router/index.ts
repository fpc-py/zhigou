import { createRouter, createWebHistory } from 'vue-router';
import { useUserStore } from '@/stores/user';

/**
 * 路由：对齐原型 8 屏 + 交易闭环页
 * - meta.tabbar = false 时隐藏底部 TabBar（chat/detail/compare/tryon/交易页等二级页）
 */
const router = createRouter({
  history: createWebHistory(import.meta.env.BASE_URL),
  routes: [
    // ── 主 Tab 页 ──
    { path: '/', name: 'home', component: () => import('@/views/home/index.vue') },
    { path: '/chat', name: 'chat', component: () => import('@/views/chat/index.vue'), meta: { requiresAuth: true, tabbar: false } },
    { path: '/closet', name: 'closet', component: () => import('@/views/closet/index.vue'), meta: { requiresAuth: true } },
    { path: '/community', name: 'community', component: () => import('@/views/community/index.vue') },
    { path: '/profile', name: 'profile', component: () => import('@/views/profile/index.vue'), meta: { requiresAuth: true } },

    // ── 二级页（隐藏 TabBar） ──
    { path: '/login', name: 'login', component: () => import('@/views/login/index.vue'), meta: { tabbar: false } },
    { path: '/products', name: 'products', component: () => import('@/views/products/index.vue'), meta: { requiresAuth: true, tabbar: false } },
    { path: '/product/:spuId', name: 'product', component: () => import('@/views/product/[spuId].vue'), meta: { requiresAuth: true, tabbar: false } },
    { path: '/compare', name: 'compare', component: () => import('@/views/compare/index.vue'), meta: { requiresAuth: true, tabbar: false } },
    { path: '/tryon', name: 'tryon', component: () => import('@/views/tryon/index.vue'), meta: { requiresAuth: true, tabbar: false } },

    // ── 交易闭环 ──
    { path: '/cart', name: 'cart', component: () => import('@/views/cart/index.vue'), meta: { requiresAuth: true, tabbar: false } },
    { path: '/checkout', name: 'checkout', component: () => import('@/views/checkout/index.vue'), meta: { requiresAuth: true, tabbar: false } },
    { path: '/orders', name: 'orders', component: () => import('@/views/orders/index.vue'), meta: { requiresAuth: true, tabbar: false } },
    { path: '/order/:orderId', name: 'order-detail', component: () => import('@/views/order-detail/index.vue'), meta: { requiresAuth: true, tabbar: false } },
    { path: '/address', name: 'address', component: () => import('@/views/address/index.vue'), meta: { requiresAuth: true, tabbar: false } },
    { path: '/coupons', name: 'coupons', component: () => import('@/views/coupons/index.vue'), meta: { requiresAuth: true, tabbar: false } },
    { path: '/wallet', name: 'wallet', component: () => import('@/views/wallet/index.vue'), meta: { requiresAuth: true, tabbar: false } },
    { path: '/merchant', name: 'merchant', component: () => import('@/views/merchant/index.vue'), meta: { requiresAuth: true, tabbar: false } },
    { path: '/groupbuy', name: 'groupbuy', component: () => import('@/views/groupbuy/index.vue'), meta: { requiresAuth: true, tabbar: false } },
    { path: '/short-video', name: 'short-video', component: () => import('@/views/short-video/index.vue'), meta: { requiresAuth: true, tabbar: false } },
    { path: '/live', name: 'live', component: () => import('@/views/live/index.vue'), meta: { requiresAuth: true, tabbar: false } },
    { path: '/live/:id', name: 'live-detail', component: () => import('@/views/live/detail.vue'), meta: { requiresAuth: true, tabbar: false } },
    { path: '/life', name: 'life', component: () => import('@/views/life/index.vue'), meta: { requiresAuth: true, tabbar: false } },
    { path: '/life/store/:id', name: 'life-store', component: () => import('@/views/life/store.vue'), meta: { requiresAuth: true, tabbar: false } },
    { path: '/life/appointments', name: 'life-appointments', component: () => import('@/views/life/appointments.vue'), meta: { requiresAuth: true, tabbar: false } },

    // ── 售后 ──
    { path: '/aftersale/apply', name: 'aftersale-apply', component: () => import('@/views/aftersale/apply.vue'), meta: { requiresAuth: true, tabbar: false } },
    { path: '/aftersale/:no', name: 'aftersale-detail', component: () => import('@/views/aftersale/detail.vue'), meta: { requiresAuth: true, tabbar: false } },
  ],
});

router.beforeEach((to) => {
  if (to.meta.requiresAuth) {
    const userStore = useUserStore();
    if (!userStore.isLoggedIn) return '/login';
  }
});

export default router;
