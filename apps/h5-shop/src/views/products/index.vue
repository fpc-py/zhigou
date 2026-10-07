<template>
  <div class="page products-page">
    <header class="page-head">
      <button class="head-btn" @click="router.back()"><Icon name="back" /></button>
      <span class="head-title">全部商品</span>
      <span class="head-spacer" />
    </header>

    <!-- 搜索 -->
    <div class="search-bar">
      <input v-model="keyword" placeholder="搜索商品…" @keyup.enter="search" />
      <button class="search-btn" @click="search"><Icon name="search" size="sm" /></button>
    </div>

    <Skeleton v-if="loading" w="100%" h="200px" :repeat="3" />
    <ErrorRetry v-else-if="error" text="加载失败，请检查网络" btn-text="重试" @retry="load" />
    <EmptyState v-else-if="products.length === 0" illustration="📭" text="暂无商品" />

    <div v-else class="product-grid">
      <ProductCard v-for="p in products" :key="p.spuId" :product="p" />
    </div>
  </div>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { getProductPage } from '@/api/product';
import type { ProductPageItem } from '@/api/product';
import Skeleton from '@/components/Skeleton.vue';
import ErrorRetry from '@/components/ErrorRetry.vue';
import EmptyState from '@/components/EmptyState.vue';
import ProductCard from '@/components/ProductCard.vue';
import Icon from '@/components/Icon.vue';

const route = useRoute();
const router = useRouter();
const loading = ref(true);
const error = ref(false);
const products = ref<ProductPageItem[]>([]);
const keyword = ref('');

async function load() {
  loading.value = true;
  error.value = false;
  try {
    const res = await getProductPage({ keyword: keyword.value.trim() || undefined, pageSize: 20 });
    products.value = res.records;
  } catch {
    error.value = true;
  } finally {
    loading.value = false;
  }
}

function search() {
  load();
}

onMounted(() => {
  const q = route.query.q;
  if (typeof q === 'string' && q.trim()) {
    keyword.value = q;
  }
  load();
});
</script>

<style scoped>
.products-page { min-height: 100vh; padding: 0 16px 30px; }
.page-head { display: flex; align-items: center; justify-content: space-between; padding: 12px 0; }
.head-btn { width: 32px; height: 32px; display: flex; align-items: center; justify-content: center; }
.head-title { font-size: 15px; font-weight: 700; }
.head-spacer { width: 32px; }
.search-bar { display: flex; gap: 8px; margin-bottom: 14px; }
.search-bar input { flex: 1; height: 40px; padding: 0 14px; background: var(--card); border-radius: 999px; font-size: 13.5px; border: 1px solid var(--line); }
.search-btn { width: 40px; height: 40px; border-radius: 50%; background: var(--brand); color: #fff; display: flex; align-items: center; justify-content: center; flex: none; }
.product-grid { display: grid; grid-template-columns: 1fr 1fr; gap: 10px; }
</style>
