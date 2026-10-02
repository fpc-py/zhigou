<template>
  <div class="page profile-page">
    <!-- 用户信息 -->
    <div class="user-header">
      <div class="avatar">👤</div>
      <div class="user-info">
        <h3>智购用户</h3>
        <p>查看个人资料</p>
      </div>
    </div>

    <!-- 订单入口 -->
    <div class="section">
      <h4 class="section-title">我的订单</h4>
      <div class="order-tabs">
        <div v-for="t in orderTabs" :key="t.label" class="order-tab">
          <span class="order-icon">{{ t.icon }}</span>
          <span class="order-label">{{ t.label }}</span>
        </div>
      </div>
    </div>

    <!-- 服务入口 -->
    <div class="section">
      <div
        v-for="item in menuItems"
        :key="item.label"
        class="menu-item"
      >
        <span class="menu-icon">{{ item.icon }}</span>
        <span class="menu-label">{{ item.label }}</span>
        <span class="menu-arrow">›</span>
      </div>
    </div>

    <!-- 退出 -->
    <button class="logout-btn" @click="logout">退出登录</button>

    <TabBar />
  </div>
</template>

<script setup lang="ts">
import { useRouter } from 'vue-router';
import { useUserStore } from '@/stores/user';

const router = useRouter();
const userStore = useUserStore();

const orderTabs = [
  { icon: '📋', label: '全部' },
  { icon: '💳', label: '待付款' },
  { icon: '📦', label: '待发货' },
  { icon: '🚚', label: '待收货' },
];

const menuItems = [
  { icon: '📍', label: '收货地址' },
  { icon: '🎫', label: '优惠券' },
  { icon: '⚙️', label: '设置' },
];

function logout() {
  userStore.clearToken();
  router.push('/login');
}
</script>

<style scoped>
.profile-page {
  padding-bottom: 58px;
}
.user-header {
  display: flex;
  align-items: center;
  gap: 16px;
  padding: 24px 16px;
  background: var(--card);
}
.avatar {
  width: 56px;
  height: 56px;
  border-radius: 50%;
  background: var(--line);
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 28px;
}
.user-info h3 {
  font-size: 17px;
}
.user-info p {
  color: var(--ink-3);
  font-size: 13px;
  margin-top: 2px;
}
.section {
  margin: 10px 0;
  background: var(--card);
  padding: 16px;
}
.section-title {
  font-size: 14px;
  font-weight: 600;
  margin-bottom: 12px;
}
.order-tabs {
  display: flex;
  justify-content: space-around;
}
.order-tab {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 4px;
  cursor: pointer;
}
.order-icon {
  font-size: 22px;
}
.order-label {
  font-size: 12px;
  color: var(--ink-2);
}
.menu-item {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 14px 0;
  border-bottom: 1px solid var(--line);
}
.menu-item:last-child {
  border-bottom: none;
}
.menu-icon {
  font-size: 18px;
}
.menu-label {
  flex: 1;
  font-size: 14px;
}
.menu-arrow {
  color: var(--ink-3);
  font-size: 20px;
}
.logout-btn {
  width: calc(100% - 32px);
  margin: 20px 16px;
  height: 44px;
  border-radius: 999px;
  background: var(--card);
  color: var(--danger);
  font-size: 15px;
  font-weight: 500;
  border: 1px solid var(--danger);
}
</style>