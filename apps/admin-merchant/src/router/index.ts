import { createRouter, createWebHistory } from 'vue-router';
import { useUserStore } from '@/stores/user';

const router = createRouter({
  history: createWebHistory(import.meta.env.BASE_URL),
  routes: [
    { path: '/login', name: 'login', component: () => import('@/views/login/index.vue') },
    {
      path: '/',
      component: () => import('@/layout/MainLayout.vue'),
      redirect: '/dashboard',
      children: [
        { path: 'dashboard', name: 'dashboard', component: () => import('@/views/dashboard/index.vue'), meta: { title: '数据看板' } },
        { path: 'product', name: 'product', component: () => import('@/views/product/index.vue'), meta: { title: '商品管理' } },
        { path: 'product/edit/:spuId?', name: 'productEdit', component: () => import('@/views/product/edit.vue'), meta: { title: '商品编辑' } },
        { path: 'order', name: 'order', component: () => import('@/views/order/index.vue'), meta: { title: '订单管理' } },
        { path: 'order/:id', name: 'orderDetail', component: () => import('@/views/order/detail.vue'), meta: { title: '订单详情' } },
        { path: 'aftersale', name: 'aftersale', component: () => import('@/views/aftersale/index.vue'), meta: { title: '售后审核' } },
      ],
    },
  ],
});

router.beforeEach((to) => {
  if (to.path !== '/login') {
    const userStore = useUserStore();
    if (!userStore.isLoggedIn) return '/login';
  }
});

export default router;