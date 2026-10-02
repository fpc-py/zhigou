<template>
  <div class="page home-page">
    <!-- 顶部 AI 对话输入条 -->
    <div class="chat-bar" @click="goChat">
      <span class="chat-icon">💬</span>
      <span class="chat-placeholder">问问小智，想买什么？</span>
      <span class="chat-arrow">→</span>
    </div>

    <!-- 快捷入口 -->
    <div class="quick-entries">
      <div v-for="e in entries" :key="e.label" class="entry" @click="e.action">
        <span class="entry-icon">{{ e.icon }}</span>
        <span class="entry-label">{{ e.label }}</span>
      </div>
    </div>

    <!-- 商品流：三种状态 -->
    <div v-if="loading" class="feed-section">
      <Skeleton w="100%" h="200px" :repeat="3" />
    </div>

    <div v-else-if="error" class="feed-section">
      <ErrorRetry text="加载失败，请检查网络" btn-text="重试" @retry="loadFeed" />
    </div>

    <div v-else-if="products.length === 0" class="feed-section">
      <EmptyState illustration="📭" text="暂无商品" />
    </div>

    <div v-else class="feed-section">
      <h3 class="section-title">推荐商品</h3>
      <div class="product-grid">
        <ProductCard v-for="p in products" :key="p.spuId" :product="p" />
      </div>
    </div>

    <TabBar />
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue';
import { useRouter } from 'vue-router';
import { getHomeFeed } from '@/api/home';
import type { ProductItem } from '@/api/home';

const router = useRouter();
const loading = ref(true);
const error = ref(false);
const products = ref<ProductItem[]>([]);

const entries = [
  { icon: '🛍️', label: '全部商品', action: () => {} },
  { icon: '🎫', label: '优惠券', action: () => router.push('/profile') },
  { icon: '📦', label: '订单', action: () => router.push('/profile') },
  { icon: '💁', label: '客服', action: () => router.push('/chat') },
];

async function loadFeed() {
  loading.value = true;
  error.value = false;
  try {
    const data = await getHomeFeed();
    products.value = data.products;
  } catch {
    error.value = true;
  } finally {
    loading.value = false;
  }
}

function goChat() {
  router.push('/chat');
}

onMounted(loadFeed);
</script>

<style scoped>
.home-page {
  padding-bottom: 58px;
  min-height: 100vh;
}
.chat-bar {
  margin: 16px;
  padding: 14px 16px;
  background: var(--card);
  border-radius: 999px;
  display: flex;
  align-items: center;
  gap: 10px;
  box-shadow: var(--shadow);
  cursor: pointer;
}
.chat-placeholder {
  flex: 1;
  color: var(--ink-3);
  font-size: 15px;
}
.chat-arrow {
  color: var(--brand);
  font-weight: 700;
}
.quick-entries {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 8px;
  padding: 0 16px;
  margin-bottom: 16px;
}
.entry {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 6px;
  padding: 14px 0;
  background: var(--card);
  border-radius: var(--radius-sm);
  cursor: pointer;
}
.entry-icon {
  font-size: 24px;
}
.entry-label {
  font-size: 12px;
  color: var(--ink-2);
}
.feed-section {
  padding: 0 16px;
}
.section-title {
  font-size: 16px;
  font-weight: 600;
  margin-bottom: 12px;
}
.product-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 10px;
}
</style>