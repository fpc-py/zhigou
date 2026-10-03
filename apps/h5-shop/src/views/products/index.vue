<template>
  <div class="page products-page">
    <h3 class="page-title">全部商品</h3>

    <Skeleton v-if="loading" w="100%" h="200px" :repeat="3" />

    <ErrorRetry
      v-else-if="error"
      text="加载失败，请检查网络"
      btn-text="重试"
      @retry="load"
    />

    <EmptyState v-else-if="products.length === 0" illustration="📭" text="暂无商品" />

    <div v-else class="product-grid">
      <ProductCard v-for="p in products" :key="p.spuId" :product="p" />
    </div>

    <TabBar />
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue';
import { getHomeFeed } from '@/api/home';
import type { ProductItem } from '@/api/home';
import Skeleton from '@/components/Skeleton.vue';
import ErrorRetry from '@/components/ErrorRetry.vue';
import EmptyState from '@/components/EmptyState.vue';
import ProductCard from '@/components/ProductCard.vue';
import TabBar from '@/components/TabBar.vue';

const loading = ref(true);
const error = ref(false);
const products = ref<ProductItem[]>([]);

// 复用首页 feed 数据（BFF 目前仅提供 /home/feed 聚合接口）
async function load() {
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

onMounted(load);
</script>

<style scoped>
.products-page {
  padding: 0 16px 74px;
}
.page-title {
  font-size: 18px;
  font-weight: 700;
  padding: 16px 0 12px;
}
.product-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 10px;
}
</style>
