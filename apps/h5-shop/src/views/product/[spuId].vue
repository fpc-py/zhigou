<template>
  <div class="page detail-page">
    <!-- Loading -->
    <Skeleton v-if="loading" w="100%" h="300px" :repeat="3" />

    <!-- Error -->
    <ErrorRetry
      v-else-if="error"
      text="商品信息加载失败"
      btn-text="重试"
      @retry="loadDetail"
    />

    <!-- Empty -->
    <EmptyState v-else-if="!detail.product" illustration="📭" text="商品不存在" />

    <!-- Content -->
    <template v-else>
      <!-- 商品图 -->
      <div class="gallery">
        <div class="gallery-placeholder">📷</div>
      </div>

      <!-- 基本信息 -->
      <div class="info-section">
        <h1 class="product-name">{{ detail.product.name }}</h1>
        <p class="product-subtitle" v-if="detail.product.subtitle">
          {{ detail.product.subtitle }}
        </p>
        <p class="product-price">
          ¥{{ ((detail.product.priceMin || 0) / 100).toFixed(2) }}
        </p>
        <p v-if="detail.product.salesVolume" class="product-sales">
          已售 {{ detail.product.salesVolume }}
        </p>
      </div>

      <!-- AI 推荐理由 -->
      <div v-if="detail.aiReason" class="ai-reason">
        <div class="reason-header">🤖 小智说</div>
        <p class="reason-text">{{ detail.aiReason }}</p>
      </div>

      <!-- 加购按钮 -->
      <div class="buy-bar">
        <button class="cart-btn" @click="addToCart">加入购物车</button>
        <button class="buy-btn">立即购买</button>
      </div>
    </template>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue';
import { useRoute } from 'vue-router';
import { getProductDetail } from '@/api/product';
import { useCartStore } from '@/stores/cart';
import type { ProductDetailData } from '@/api/product';
import Skeleton from '@/components/Skeleton.vue';
import ErrorRetry from '@/components/ErrorRetry.vue';
import EmptyState from '@/components/EmptyState.vue';

const route = useRoute();
const cartStore = useCartStore();
const spuId = route.params.spuId as string;
const loading = ref(true);
const error = ref(false);
const detail = ref<ProductDetailData>({ product: null, aiReason: null });

async function loadDetail() {
  loading.value = true;
  error.value = false;
  try {
    detail.value = await getProductDetail(spuId);
  } catch {
    error.value = true;
  } finally {
    loading.value = false;
  }
}

function addToCart() {
  if (!detail.value.product) return;
  cartStore.addItem({
    spuId: Number(spuId),
    name: detail.value.product.name || '',
    price: detail.value.product.priceMin || 0,
    count: 1,
  });
  alert('已加入购物车');
}

onMounted(loadDetail);
</script>

<style scoped>
.detail-page {
  padding-bottom: 80px;
}
.gallery {
  width: 100%;
  aspect-ratio: 1;
  background: var(--line);
  display: flex;
  align-items: center;
  justify-content: center;
}
.gallery-placeholder {
  font-size: 64px;
  opacity: 0.3;
}
.info-section {
  padding: 16px;
  background: var(--card);
  margin: 0 0 10px;
}
.product-name {
  font-size: 18px;
  font-weight: 700;
}
.product-subtitle {
  color: var(--ink-2);
  font-size: 13px;
  margin-top: 6px;
}
.product-price {
  color: var(--accent);
  font-size: 24px;
  font-weight: 700;
  margin-top: 10px;
}
.product-sales {
  color: var(--ink-3);
  font-size: 12px;
  margin-top: 4px;
}
.ai-reason {
  margin: 0 16px 16px;
  padding: 14px;
  background: var(--brand-soft);
  border-radius: var(--radius-sm);
}
.reason-header {
  font-weight: 600;
  font-size: 13px;
  margin-bottom: 6px;
}
.reason-text {
  font-size: 13px;
  color: var(--ink-2);
  line-height: 1.6;
}
.buy-bar {
  position: fixed;
  bottom: 0;
  left: 50%;
  transform: translateX(-50%);
  width: 100%;
  max-width: 414px;
  display: flex;
  gap: 10px;
  padding: 12px 16px;
  background: var(--card);
  border-top: 1px solid var(--line);
}
.cart-btn, .buy-btn {
  flex: 1;
  height: 44px;
  border-radius: 999px;
  font-size: 15px;
  font-weight: 600;
}
.cart-btn {
  background: var(--brand-soft);
  color: var(--brand);
  border: 1px solid var(--brand);
}
.buy-btn {
  background: var(--brand);
  color: #fff;
}
</style>