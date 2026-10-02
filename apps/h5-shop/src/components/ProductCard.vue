<template>
  <div class="product-card" @click="goDetail">
    <div class="card-image">
      <div v-if="!product.mainImage" class="img-placeholder">📦</div>
      <img v-else :src="product.mainImage" :alt="product.name" />
    </div>
    <div class="card-info">
      <h4 class="card-name">{{ product.name }}</h4>
      <p class="card-price">¥{{ (product.priceMin / 100).toFixed(2) }}</p>
      <p v-if="product.salesVolume" class="card-sales">已售 {{ product.salesVolume }}</p>
    </div>
  </div>
</template>

<script setup lang="ts">
import { useRouter } from 'vue-router';
import type { ProductItem } from '@/api/home';

const props = defineProps<{ product: ProductItem }>();
const router = useRouter();

function goDetail() {
  router.push(`/product/${props.product.spuId}`);
}
</script>

<style scoped>
.product-card {
  background: var(--card);
  border-radius: var(--radius-sm);
  overflow: hidden;
  cursor: pointer;
}
.card-image {
  width: 100%;
  aspect-ratio: 1;
  background: var(--line);
  display: flex;
  align-items: center;
  justify-content: center;
}
.img-placeholder {
  font-size: 32px;
}
.card-image img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}
.card-info {
  padding: 10px;
}
.card-name {
  font-size: 13px;
  font-weight: 500;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.card-price {
  color: var(--accent);
  font-weight: 700;
  font-size: 16px;
  margin-top: 4px;
}
.card-sales {
  color: var(--ink-3);
  font-size: 11px;
  margin-top: 2px;
}
</style>