import { createRouter, createWebHistory } from 'vue-router';
import { useUserStore } from '@/stores/user';

const router = createRouter({
  history: createWebHistory(import.meta.env.BASE_URL),
  routes: [
    { path: '/', name: 'home', component: () => import('@/views/home/index.vue') },
    { path: '/login', name: 'login', component: () => import('@/views/login/index.vue') },
    { path: '/chat', name: 'chat', component: () => import('@/views/chat/index.vue'), meta: { requiresAuth: true } },
    { path: '/product/:spuId', name: 'product', component: () => import('@/views/product/[spuId].vue'), meta: { requiresAuth: true } },
    { path: '/compare', name: 'compare', component: () => import('@/views/compare/index.vue'), meta: { requiresAuth: true } },
    { path: '/profile', name: 'profile', component: () => import('@/views/profile/index.vue'), meta: { requiresAuth: true } },
  ],
});

router.beforeEach((to) => {
  if (to.meta.requiresAuth) {
    const userStore = useUserStore();
    if (!userStore.isLoggedIn) return '/login';
  }
});

export default router;
