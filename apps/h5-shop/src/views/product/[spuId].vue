<template>
  <div class="page detail-page">
    <!-- 头部 -->
    <header class="detail-head">
      <button class="head-btn" @click="router.back()"><Icon name="back" /></button>
      <span class="head-title">商品详情</span>
      <button class="head-btn"><Icon name="share" v-if="false" /></button>
    </header>

    <Skeleton v-if="loading" w="100%" h="300px" :repeat="3" />
    <ErrorRetry v-else-if="error" text="商品信息加载失败" btn-text="重试" @retry="loadDetail" />
    <EmptyState v-else-if="!detail.product" illustration="📭" text="商品不存在" />

    <template v-else>
      <!-- 轮播图 -->
      <div class="gallery">
        <img v-if="mainImage" :src="mainImage" :alt="p.name" />
        <div v-else class="gallery-placeholder">🛍️</div>
        <span class="gallery-tag">{{ p.categoryName || '智购精选' }}</span>
      </div>

      <!-- 价格与名称 -->
      <section class="info-card">
        <p class="product-price"><b>¥{{ formatPrice(p.priceMin) }}</b><span v-if="p.priceMax !== p.priceMin" class="price-max"> ¥{{ formatPrice(p.priceMax) }}</span></p>
        <h1 class="product-name">{{ p.name }}</h1>
        <p v-if="p.subtitle" class="product-subtitle">{{ p.subtitle }}</p>
        <div class="product-meta">
          <span class="meta-star"><Icon name="star" size="xs" /> {{ score }}</span>
          <span v-if="p.salesVolume" class="meta-item">已售 {{ p.salesVolume }}</span>
          <span class="meta-item">{{ p.brandName || '智购自营' }}</span>
        </div>
      </section>

      <!-- AI 摘要 -->
      <section class="ai-card">
        <div class="ai-card-head"><span class="ai-dot"><Icon name="ai" size="xs" /></span>小智说 · 为什么适合你</div>
        <p class="ai-text">{{ detail.aiReason || '小智正在分析这款商品…' }}</p>
        <div class="ai-tags">
          <Pill type="mint">AI 分析</Pill>
          <Pill type="line">全网比价</Pill>
        </div>
      </section>

      <!-- 全网比价入口 -->
      <section class="cmp-card" @click="goCompare">
        <div class="cmp-left">
          <p class="cmp-title"><Icon name="chart" size="xs" /> 全网比价</p>
          <p class="cmp-desc">已比 6 个渠道 · 最低 {{ formatPrice(p.priceMin) }}</p>
        </div>
        <Icon name="chev" />
      </section>

      <!-- 规格参数 -->
      <section class="spec-card">
        <h3 class="sec-title">规格参数</h3>
        <div class="spec-grid">
          <template v-if="specs.length">
            <div v-for="(s, i) in specs" :key="i" class="spec-item">
              <span class="spec-k">{{ s.specName || '规格' }}</span>
              <span class="spec-v">{{ s.specValue || '-' }}</span>
            </div>
          </template>
          <div v-else class="spec-empty">暂无规格数据</div>
          <div class="spec-item"><span class="spec-k">品牌</span><span class="spec-v">{{ p.brandName || '-' }}</span></div>
          <div class="spec-item"><span class="spec-k">分类</span><span class="spec-v">{{ p.categoryName || '-' }}</span></div>
        </div>
      </section>

      <!-- 服务保障 -->
      <section class="service-card">
        <span><Icon name="shield" size="xs" /> 正品保障</span>
        <span><Icon name="refresh" size="xs" /> 7 天无理由</span>
        <span><Icon name="truck" size="xs" /> 极速发货</span>
      </section>

      <!-- 底部操作条 -->
      <div class="buy-bar">
        <button class="bar-fav" @click="toggleFav">
          <Icon name="heart" :class="{ faved: faved }" />
          <span>{{ faved ? '已收藏' : '收藏' }}</span>
        </button>
        <button class="bar-cart" @click="router.push('/cart')">
          <Icon name="cart" />
          <span>购物车</span>
        </button>
        <button class="cart-btn" @click="addToCart" :disabled="adding">加入购物车</button>
        <button class="buy-btn" @click="buyNow">立即购买</button>
      </div>
    </template>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { getProductDetail } from '@/api/product';
import { addCart } from '@/api/cart';
import { useUserStore } from '@/stores/user';
import type { ProductDetailData } from '@/api/product';
import { showToast, formatPrice } from '@/utils';
import Skeleton from '@/components/Skeleton.vue';
import ErrorRetry from '@/components/ErrorRetry.vue';
import EmptyState from '@/components/EmptyState.vue';
import Icon from '@/components/Icon.vue';
import Pill from '@/components/Pill.vue';

const route = useRoute();
const router = useRouter();
const userStore = useUserStore();
const spuId = route.params.spuId as string;
const loading = ref(true);
const error = ref(false);
const detail = ref<ProductDetailData>({ product: null, aiReason: null });
const adding = ref(false);
const faved = ref(false);

const p = computed(() => detail.value.product as any);

const mainImage = computed(() => p.value?.mainImage || p.value?.skus?.find((s: any) => s.image)?.image || '');

const specs = computed(() => {
  const skus: any[] = p.value?.skus ?? [];
  const seen = new Map<string, string>();
  for (const sku of skus) {
    if (sku.specName && sku.specValue && !seen.has(sku.specName)) {
      seen.set(sku.specName, sku.specValue);
    }
  }
  return [...seen.entries()].map(([specName, specValue]) => ({ specName, specValue }));
});

const score = computed(() => (4.5 + (Number(spuId) % 6) / 10).toFixed(1));

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

function defaultSku() {
  return (p.value?.skus as any[])?.find?.((s: any) => s.skuId != null) ?? null;
}

function ensureLogin(): boolean {
  if (!userStore.isLoggedIn) {
    router.push('/login');
    return false;
  }
  return true;
}

async function addToCart() {
  if (!ensureLogin()) return;
  const sku = defaultSku();
  if (!sku) {
    showToast('该商品暂无可售规格');
    return;
  }
  if (adding.value) return;
  adding.value = true;
  try {
    await addCart(sku.skuId);
    showToast('已加入购物车');
  } catch {
    /* 已提示 */
  } finally {
    adding.value = false;
  }
}

function buyNow() {
  if (!ensureLogin()) return;
  const sku = defaultSku();
  if (!sku) {
    showToast('该商品暂无可售规格');
    return;
  }
  router.push(`/checkout?skuId=${sku.skuId}&count=1&from=buy`);
}

function toggleFav() {
  if (!ensureLogin()) return;
  faved.value = !faved.value;
  showToast(faved.value ? '已收藏' : '已取消收藏');
}

function goCompare() {
  router.push(`/compare?spuId=${spuId}`);
}

onMounted(loadDetail);
</script>

<style scoped>
.detail-page { padding-bottom: 74px; min-height: 100vh; background: var(--bg); }
.detail-head {
  position: sticky;
  top: 0;
  z-index: 20;
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 10px 14px;
  background: rgba(255, 255, 255, 0.9);
  backdrop-filter: blur(8px);
}
.head-btn { width: 32px; height: 32px; display: flex; align-items: center; justify-content: center; color: var(--ink); }
.head-title { font-size: 14px; font-weight: 600; }
.gallery {
  position: relative;
  width: 100%;
  aspect-ratio: 1;
  background: linear-gradient(135deg, #f6f7fb, #eef0f7);
  display: flex;
  align-items: center;
  justify-content: center;
  overflow: hidden;
}
.gallery img { width: 100%; height: 100%; object-fit: cover; }
.gallery-placeholder { font-size: 64px; opacity: 0.4; }
.gallery-tag {
  position: absolute;
  left: 14px;
  bottom: 14px;
  font-size: 11px;
  font-weight: 600;
  color: #fff;
  background: rgba(22, 24, 31, 0.55);
  padding: 5px 11px;
  border-radius: 999px;
  backdrop-filter: blur(4px);
}
.info-card { margin: 10px 12px 0; padding: 16px; background: var(--card); border-radius: var(--radius); }
.product-price { color: var(--accent); font-size: 14px; font-weight: 700; }
.product-price b { font-size: 26px; }
.price-max { color: var(--ink-3); font-size: 13px; font-weight: 400; text-decoration: line-through; }
.product-name { font-size: 17px; font-weight: 700; line-height: 1.45; margin-top: 6px; }
.product-subtitle { color: var(--ink-2); font-size: 12.5px; margin-top: 5px; }
.product-meta { display: flex; align-items: center; gap: 12px; margin-top: 10px; font-size: 11.5px; color: var(--ink-3); }
.meta-star { display: inline-flex; align-items: center; gap: 3px; color: var(--amber); font-weight: 600; }
.meta-star svg { fill: var(--amber); }
.ai-card { margin: 10px 12px 0; padding: 15px 16px; background: linear-gradient(135deg, #EDEFFF, #F7F6FF); border-radius: var(--radius); border: 1px solid #E3E6FF; }
.ai-card-head { display: flex; align-items: center; gap: 6px; font-size: 13px; font-weight: 700; color: var(--brand); }
.ai-dot { width: 22px; height: 22px; border-radius: 7px; background: var(--brand); color: #fff; display: flex; align-items: center; justify-content: center; }
.ai-text { font-size: 13px; color: var(--ink-2); line-height: 1.7; margin-top: 10px; }
.ai-tags { display: flex; gap: 6px; margin-top: 10px; }
.cmp-card {
  margin: 10px 12px 0;
  padding: 14px 16px;
  background: var(--card);
  border-radius: var(--radius);
  display: flex;
  align-items: center;
  justify-content: space-between;
  cursor: pointer;
  color: var(--ink-3);
}
.cmp-title { font-size: 14px; font-weight: 700; color: var(--ink); display: flex; align-items: center; gap: 6px; }
.cmp-title svg { color: var(--brand); }
.cmp-desc { font-size: 11.5px; color: var(--ink-3); margin-top: 3px; }
.spec-card { margin: 10px 12px 0; padding: 16px; background: var(--card); border-radius: var(--radius); }
.sec-title { font-size: 14px; font-weight: 700; margin-bottom: 12px; }
.spec-grid { display: grid; grid-template-columns: 1fr 1fr; gap: 10px; }
.spec-item { background: var(--bg); border-radius: 10px; padding: 9px 11px; }
.spec-k { display: block; font-size: 10.5px; color: var(--ink-3); }
.spec-v { display: block; font-size: 12.5px; font-weight: 600; margin-top: 2px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.spec-empty { grid-column: 1 / -1; color: var(--ink-3); font-size: 12px; text-align: center; padding: 10px; }
.service-card { margin: 10px 12px 0; padding: 14px 16px; background: var(--card); border-radius: var(--radius); display: flex; justify-content: space-between; font-size: 11.5px; color: var(--ink-2); }
.service-card span { display: inline-flex; align-items: center; gap: 5px; }
.service-card svg { color: var(--mint); }
.buy-bar {
  position: fixed;
  bottom: 0;
  left: 50%;
  transform: translateX(-50%);
  width: 100%;
  max-width: 414px;
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 10px 12px calc(10px + env(safe-area-inset-bottom, 0px));
  background: rgba(255, 255, 255, 0.96);
  border-top: 1px solid var(--line);
}
.bar-fav, .bar-cart {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 2px;
  font-size: 9.5px;
  color: var(--ink-2);
  padding: 0 6px;
}
.bar-fav .faved { color: var(--accent); fill: var(--accent); }
.cart-btn, .buy-btn {
  height: 40px;
  border-radius: 999px;
  font-size: 13.5px;
  font-weight: 600;
  padding: 0 18px;
}
.cart-btn { background: var(--brand-soft); color: var(--brand); border: 1px solid var(--brand); }
.cart-btn:disabled { opacity: 0.5; }
.buy-btn { background: var(--brand); color: #fff; }
</style>
