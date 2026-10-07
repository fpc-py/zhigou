<template>
  <div class="page home-page">
    <!-- 问候头部 -->
    <header class="greet">
      <div class="greet-left">
        <div class="avatar">{{ avatarChar }}</div>
        <div>
          <p class="greet-hi">{{ greeting }}，{{ nickname }}</p>
          <p class="greet-date">{{ todayText }} · <span class="ai-ready"><Icon name="ai" size="xs" /> 小智已就绪</span></p>
        </div>
      </div>
      <div class="save-chip"><Icon name="tag" size="xs" /> 今日省 ¥86</div>
    </header>

    <!-- AI 输入条 -->
    <div class="chat-bar" @click="goChat('')">
      <div class="chat-avatar"><Icon name="ai" size="sm" /></div>
      <span class="chat-placeholder">问问小智，想买什么？</span>
      <span class="chat-btn"><Icon name="arrow-up" size="sm" /></span>
    </div>

    <!-- 快捷入口 4 宫格 -->
    <div class="quick-grid">
      <button v-for="e in entries" :key="e.label" class="entry" @click="e.action">
        <span class="entry-ic" :style="{ background: e.bg, color: e.color }"><Icon :name="e.icon" /></span>
        <span class="entry-label">{{ e.label }}</span>
        <span class="entry-sub">{{ e.sub }}</span>
      </button>
    </div>

    <!-- Banner -->
    <div class="banner" @click="goChat('帮我挑一份礼物，预算 300 元')">
      <div class="banner-text">
        <p class="banner-title">AI 帮你买得更好</p>
        <p class="banner-sub">全网比价 · 智能衣橱 · AR 试穿</p>
      </div>
      <div class="banner-balls" aria-hidden="true"><i /><i /><i /></div>
    </div>

    <!-- 商品流 -->
    <div v-if="loading" class="feed-section">
      <Skeleton w="100%" h="200px" :repeat="3" />
    </div>

    <div v-else-if="error" class="feed-section">
      <ErrorRetry text="加载失败，请检查网络" btn-text="重试" @retry="loadFeed" />
    </div>

    <div v-else-if="!userStore.isLoggedIn" class="feed-section">
      <div class="login-prompt" @click="router.push('/login')">
        <p class="login-title">登录后查看推荐商品</p>
        <p class="login-desc">手机号一键登录，开启 AI 导购体验</p>
        <button class="login-btn">立即登录 →</button>
      </div>
    </div>

    <div v-else-if="products.length === 0" class="feed-section">
      <EmptyState illustration="📭" text="暂无商品" />
    </div>

    <div v-else class="feed-section">
      <h3 class="section-title">为你推荐</h3>
      <div class="product-grid">
        <ProductCard v-for="p in products" :key="p.spuId" :product="p" />
      </div>
    </div>

    <!-- 悬浮购物车入口 -->
    <button v-if="userStore.isLoggedIn" class="cart-fab" aria-label="购物车" @click="router.push('/cart')">
      <Icon name="cart" size="lg" />
      <span v-if="cartCount > 0" class="cart-badge">{{ cartCount > 99 ? '99+' : cartCount }}</span>
    </button>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue';
import { useRouter } from 'vue-router';
import { getHomeFeed } from '@/api/home';
import { getProfile } from '@/api/user';
import { getCartMine } from '@/api/cart';
import { useUserStore } from '@/stores/user';
import type { ProductItem } from '@/api/home';
import Skeleton from '@/components/Skeleton.vue';
import ErrorRetry from '@/components/ErrorRetry.vue';
import EmptyState from '@/components/EmptyState.vue';
import ProductCard from '@/components/ProductCard.vue';
import Icon from '@/components/Icon.vue';

const router = useRouter();
const userStore = useUserStore();
const loading = ref(true);
const error = ref(false);
const products = ref<ProductItem[]>([]);
const nickname = ref('朋友');
const cartCount = ref(0);

const entries = [
  { icon: 'chart', label: 'AI全网比价', sub: '省到就是赚', bg: '#E9EBFF', color: '#4C5CFF', action: () => router.push('/compare') },
  { icon: 'gift', label: '帮我送礼', sub: 'AI 挑礼', bg: '#FFEDE7', color: '#FF5C39', action: () => goChat('帮我挑一份礼物，预算 300 元') },
  { icon: 'closet', label: '智能衣橱', sub: '每日穿搭', bg: '#E3F7F0', color: '#00A87E', action: () => router.push('/closet') },
  { icon: 'scan', label: 'AR试穿', sub: '虚拟上身', bg: '#FFF3DF', color: '#FFA62B', action: () => router.push('/tryon') },
];

const todayText = computed(() => {
  const d = new Date();
  const weeks = ['周日', '周一', '周二', '周三', '周四', '周五', '周六'];
  return `${d.getMonth() + 1}月${d.getDate()}日 ${weeks[d.getDay()]}`;
});

const greeting = computed(() => {
  const h = new Date().getHours();
  if (h < 6) return '夜深了';
  if (h < 11) return '早上好';
  if (h < 14) return '中午好';
  if (h < 18) return '下午好';
  return '晚上好';
});

const avatarChar = computed(() => (nickname.value ? nickname.value.slice(0, 1) : '夏'));

function goChat(prefill: string) {
  router.push({ path: '/chat', query: prefill ? { q: prefill } : {} });
}

async function loadProfile() {
  if (!userStore.isLoggedIn) return;
  try {
    const profile = await getProfile();
    if (profile?.nickname) nickname.value = profile.nickname;
  } catch {
    /* 忽略，用默认昵称 */
  }
}

async function loadFeed() {
  loading.value = true;
  error.value = false;
  try {
    if (!userStore.isLoggedIn) {
      products.value = [];
      loading.value = false;
      return;
    }
    const data = await getHomeFeed();
    products.value = data.products;
  } catch {
    error.value = true;
  } finally {
    loading.value = false;
  }
}

/** 加载购物车件数（悬浮入口角标） */
async function loadCartCount() {
  if (!userStore.isLoggedIn) return;
  try {
    const items = await getCartMine();
    cartCount.value = items.reduce((s, i) => s + (i.count ?? 0), 0);
  } catch {
    cartCount.value = 0;
  }
}

onMounted(() => {
  loadProfile();
  loadFeed();
  loadCartCount();
});
</script>

<style scoped>
.home-page {
  padding-bottom: 76px;
  min-height: 100vh;
}
.greet {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 18px 16px 10px;
}
.greet-left { display: flex; align-items: center; gap: 10px; }
.avatar {
  width: 40px;
  height: 40px;
  border-radius: 50%;
  background: linear-gradient(135deg, var(--brand), #8A94FF);
  color: #fff;
  font-weight: 700;
  font-size: 17px;
  display: flex;
  align-items: center;
  justify-content: center;
  flex: none;
}
.greet-hi { font-size: 15px; font-weight: 700; }
.greet-date { font-size: 11.5px; color: var(--ink-3); margin-top: 1px; display: flex; align-items: center; gap: 4px; }
.ai-ready { color: var(--mint); display: inline-flex; align-items: center; gap: 3px; font-weight: 600; }
.save-chip {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  font-size: 11px;
  font-weight: 700;
  color: var(--accent);
  background: var(--accent-soft);
  padding: 7px 11px;
  border-radius: 999px;
  flex: none;
}
.chat-bar {
  margin: 10px 16px;
  padding: 11px 12px 11px 14px;
  background: var(--card);
  border-radius: 999px;
  display: flex;
  align-items: center;
  gap: 10px;
  box-shadow: 0 6px 20px rgba(20, 26, 58, 0.08);
  cursor: pointer;
}
.chat-avatar {
  width: 26px;
  height: 26px;
  border-radius: 50%;
  background: linear-gradient(135deg, var(--brand), #8A94FF);
  color: #fff;
  display: flex;
  align-items: center;
  justify-content: center;
  flex: none;
}
.chat-placeholder { flex: 1; color: var(--ink-3); font-size: 13.5px; }
.chat-btn {
  width: 26px;
  height: 26px;
  border-radius: 50%;
  background: var(--brand);
  color: #fff;
  display: flex;
  align-items: center;
  justify-content: center;
  flex: none;
}
.quick-grid {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 10px;
  padding: 8px 16px 14px;
}
.entry {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 5px;
  background: var(--card);
  border-radius: var(--radius);
  padding: 13px 4px 11px;
  box-shadow: 0 4px 14px rgba(20, 26, 58, 0.05);
}
.entry-ic {
  width: 38px;
  height: 38px;
  border-radius: 13px;
  display: flex;
  align-items: center;
  justify-content: center;
}
.entry-label { font-size: 12px; font-weight: 600; }
.entry-sub { font-size: 9.5px; color: var(--ink-3); }
.banner {
  margin: 2px 16px 16px;
  border-radius: var(--radius);
  padding: 18px 18px;
  background: linear-gradient(120deg, #4C5CFF, #7A6BFF 55%, #9A7BFF);
  color: #fff;
  position: relative;
  overflow: hidden;
  cursor: pointer;
}
.banner-title { font-size: 17px; font-weight: 800; letter-spacing: 0.5px; }
.banner-sub { font-size: 11.5px; opacity: 0.85; margin-top: 4px; }
.banner-balls {
  position: absolute;
  right: -14px;
  top: -22px;
  width: 110px;
  height: 110px;
  border-radius: 50%;
  background: rgba(255, 255, 255, 0.14);
}
.banner-balls i {
  position: absolute;
  border-radius: 50%;
  background: rgba(255, 255, 255, 0.18);
}
.banner-balls i:nth-child(1) { width: 46px; height: 46px; right: 20px; bottom: -6px; }
.banner-balls i:nth-child(2) { width: 24px; height: 24px; right: -6px; bottom: 26px; }
.banner-balls i:nth-child(3) { width: 12px; height: 12px; right: 44px; bottom: -14px; }
.feed-section { padding: 0 16px; }
.section-title { font-size: 16px; font-weight: 700; margin-bottom: 12px; }
.product-grid { display: grid; grid-template-columns: 1fr 1fr; gap: 10px; }
.login-prompt { text-align: center; padding: 60px 24px; cursor: pointer; }
.login-title { font-size: 18px; font-weight: 600; margin-bottom: 8px; }
.login-desc { font-size: 13px; color: var(--ink-3); margin-bottom: 20px; }
.login-btn { display: inline-block; padding: 10px 32px; background: var(--brand); color: #fff; border-radius: 999px; font-size: 15px; font-weight: 500; }

/* 悬浮购物车入口（与 TabBar 对齐：按 414px 手机容器居中定位） */
.cart-fab {
  position: fixed;
  right: max(16px, calc((100vw - 414px) / 2 + 16px));
  bottom: calc(76px + env(safe-area-inset-bottom, 0px));
  width: 52px;
  height: 52px;
  border-radius: 50%;
  background: linear-gradient(135deg, var(--brand), #8A94FF);
  color: #fff;
  display: flex;
  align-items: center;
  justify-content: center;
  box-shadow: 0 8px 22px rgba(76, 92, 255, 0.38);
  z-index: 90;
  border: none;
  cursor: pointer;
  transition: transform 0.15s;
}
.cart-fab:active { transform: scale(0.92); }
.cart-badge {
  position: absolute;
  top: -4px;
  right: -4px;
  min-width: 19px;
  height: 19px;
  padding: 0 5px;
  border-radius: 999px;
  background: var(--accent);
  color: #fff;
  font-size: 11px;
  font-weight: 700;
  display: flex;
  align-items: center;
  justify-content: center;
  box-shadow: 0 2px 6px rgba(255, 92, 57, 0.4);
}
</style>
