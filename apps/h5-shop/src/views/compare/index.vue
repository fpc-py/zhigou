<template>
  <div class="page compare-page">
    <!-- 搜索框 -->
    <div class="search-bar">
      <input
        v-model="keyword"
        placeholder="输入商品名搜全网比价..."
        @keyup.enter="search"
      />
      <button class="search-btn" @click="search">搜索</button>
    </div>

    <!-- Loading -->
    <Skeleton v-if="loading" w="100%" h="80px" :repeat="5" />

    <!-- Error -->
    <ErrorRetry
      v-else-if="error"
      text="比价数据加载失败"
      btn-text="重试"
      @retry="search"
    />

    <!-- Empty: 未搜索 -->
    <EmptyState v-else-if="!searched" illustration="🔍" text="输入商品名开始比价" />

    <!-- Empty: 无结果 -->
    <EmptyState v-else-if="results.length === 0" illustration="📭" text="未找到比价数据" />

    <!-- 结果列表 -->
    <div v-else class="results">
      <div v-for="r in results" :key="r.skuId" class="result-item">
        <div class="result-spec">{{ r.specName }}: {{ r.specValue }}</div>
        <div class="result-price">¥{{ (r.price / 100).toFixed(2) }}</div>
        <div class="result-stock" :class="{ low: r.stock < 10 }">
          {{ r.stock > 0 ? (r.stock < 10 ? '仅剩' + r.stock : '有货') : '缺货' }}
        </div>
        <button class="result-cart" @click="add(r)">加购</button>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue';
import { getProductDetail } from '@/api/product';
import { useCartStore } from '@/stores/cart';

const keyword = ref('');
const loading = ref(false);
const error = ref(false);
const searched = ref(false);
const results = ref<any[]>([]);
const cartStore = useCartStore();

async function search() {
  const k = keyword.value.trim();
  if (!k) return;
  loading.value = true;
  error.value = false;
  searched.value = true;
  try {
    // 先用 RAG 搜相关 spuId，然后用 product-service 拿详情
    const detail = await getProductDetail(k);
    if (detail.product?.skus) {
      results.value = detail.product.skus;
    } else {
      results.value = [];
    }
  } catch {
    error.value = true;
  } finally {
    loading.value = false;
  }
}

function add(item: any) {
  cartStore.addItem({
    spuId: item.skuId || item.spuId,
    name: item.specValue || '',
    price: item.price || 0,
    count: 1,
  });
  alert('已加入购物车');
}
</script>

<style scoped>
.compare-page {
  padding: 16px;
}
.search-bar {
  display: flex;
  gap: 8px;
  margin-bottom: 16px;
}
.search-bar input {
  flex: 1;
  height: 40px;
  padding: 0 14px;
  background: var(--card);
  border-radius: 999px;
  font-size: 14px;
  border: 1px solid var(--line);
}
.search-btn {
  height: 40px;
  padding: 0 20px;
  background: var(--brand);
  color: #fff;
  border-radius: 999px;
  font-size: 14px;
  font-weight: 500;
}
.results {
  display: flex;
  flex-direction: column;
  gap: 8px;
}
.result-item {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 12px;
  background: var(--card);
  border-radius: var(--radius-sm);
}
.result-spec {
  flex: 1;
  font-size: 13px;
}
.result-price {
  font-weight: 700;
  color: var(--accent);
  font-size: 15px;
}
.result-stock {
  font-size: 11px;
  color: var(--mint);
  padding: 2px 8px;
  border-radius: 999px;
  background: var(--mint-soft);
}
.result-stock.low {
  color: var(--amber);
  background: var(--amber-soft);
}
.result-cart {
  padding: 6px 14px;
  background: var(--brand-soft);
  color: var(--brand);
  border-radius: 999px;
  font-size: 12px;
  font-weight: 500;
}
</style>