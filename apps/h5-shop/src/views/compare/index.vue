<template>
  <div class="page compare-page">
    <!-- 头部 -->
    <header class="cmp-head">
      <button class="head-btn" @click="router.back()"><Icon name="back" /></button>
      <span class="head-title">AI 全网比价</span>
      <span class="head-spacer" />
    </header>

    <!-- 搜索 -->
    <div class="search-bar">
      <input v-model="keyword" placeholder="输入商品名搜全网比价…" @keyup.enter="search" />
      <button class="search-btn" @click="search"><Icon name="search" size="sm" /></button>
    </div>

    <Skeleton v-if="loading" w="100%" h="80px" :repeat="5" />
    <ErrorRetry v-else-if="error" text="比价数据加载失败" btn-text="重试" @retry="search" />
    <EmptyState v-else-if="!product" illustration="🔍" :text="searched ? '未找到该商品' : '输入商品名开始比价'" />

    <template v-else-if="product">
      <!-- 最优方案 -->
      <section class="hero">
        <div class="hero-top">
          <span class="hero-badge"><Icon name="flash" size="xs" /> 最优方案</span>
          <span class="hero-save">比最高价省 {{ formatPrice(saveAmount) }}</span>
        </div>
        <p class="hero-price"><b>¥{{ formatPrice(bestTotal) }}</b><span class="hero-unit">/ {{ bestSource }}</span></p>
        <p class="hero-name">{{ product.name }}</p>
        <p class="hero-sug">{{ suggestion }}</p>
        <button class="hero-btn" @click="goBest">去购买 →</button>
      </section>

      <!-- 渠道比价表 -->
      <section class="cmp-table card">
        <h3 class="sec-title">渠道比价（含运费）</h3>
        <div v-for="c in channels" :key="c.source" class="cmp-row" :class="{ best: c.isBest }">
          <span class="cmp-name">{{ c.source }}</span>
          <span class="cmp-price"><b>¥{{ formatPrice(c.totalPrice) }}</b><i v-if="c.isBest" class="best-tag">最优</i></span>
          <span class="cmp-ship">{{ c.deliveryDays }}天 · {{ c.promoText }}</span>
          <button class="cmp-go" @click="goChannel(c)">去</button>
        </div>
        <p class="demo-note">* 渠道报价为本地比价引擎聚合（演示数据源，生产可替换为真实第三方比价 API）</p>
      </section>

      <!-- 省钱明细 -->
      <section class="save-list card">
        <h3 class="sec-title">省钱明细</h3>
        <div class="save-row"><span>最优渠道总价</span><b>¥{{ formatPrice(bestTotal) }}</b></div>
        <div class="save-row"><span>全网最高总价</span><b>¥{{ formatPrice(maxTotal) }}</b></div>
        <div class="save-row total"><span>小智帮你省下</span><b class="save-amount">¥{{ formatPrice(saveAmount) }}</b></div>
      </section>
    </template>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { getProductDetail, getProductPage } from '@/api/product';
import { comparePrices, type CompareOffer } from '@/api/price';
import { showToast, formatPrice } from '@/utils';
import Skeleton from '@/components/Skeleton.vue';
import ErrorRetry from '@/components/ErrorRetry.vue';
import EmptyState from '@/components/EmptyState.vue';
import Icon from '@/components/Icon.vue';

const route = useRoute();
const router = useRouter();
const keyword = ref('');
const loading = ref(false);
const error = ref(false);
const searched = ref(false);
const product = ref<any>(null);
const channels = ref<CompareOffer[]>([]);
const suggestion = ref('');

const bestTotal = computed(() => Math.min(...channels.value.map((c) => c.totalPrice)));
const maxTotal = computed(() => Math.max(...channels.value.map((c) => c.totalPrice)));
const saveAmount = computed(() => maxTotal.value - bestTotal.value);
const bestSource = computed(() => channels.value.find((c) => c.isBest)?.source ?? '智购自营');

async function loadCompare(spuId: string) {
  loading.value = true;
  error.value = false;
  try {
    const detail = await getProductDetail(spuId);
    product.value = detail.product;
    keyword.value = detail.product?.name ?? '';
    const firstSku = detail.product?.skus?.[0] ?? detail.product?.skuList?.[0];
    if (!firstSku?.skuId) {
      channels.value = [];
      return;
    }
    const items = await comparePrices([Number(firstSku.skuId)]);
    const item = items[0];
    channels.value = item?.offers ?? [];
    suggestion.value = item?.suggestion ?? '';
  } catch {
    error.value = true;
  } finally {
    loading.value = false;
  }
}

async function search() {
  const k = keyword.value.trim();
  if (!k) return;
  loading.value = true;
  error.value = false;
  searched.value = true;
  try {
    const res = await getProductPage({ keyword: k, pageSize: 1 });
    const first = res.records?.[0];
    if (first) {
      await loadCompare(first.spuId);
    } else {
      product.value = null;
      channels.value = [];
    }
  } catch {
    error.value = true;
  } finally {
    loading.value = false;
  }
}

function goBest() {
  if (product.value) router.push(`/product/${product.value.spuId}`);
}

function goChannel(c: CompareOffer) {
  showToast(`已为你记录「${c.source}」渠道报价：总价 ¥${formatPrice(c.totalPrice)}，约 ${c.deliveryDays} 天到货`);
  if (product.value) router.push(`/product/${product.value.spuId}`);
}

onMounted(() => {
  const spuId = route.query.spuId;
  if (typeof spuId === 'string' && spuId) {
    loadCompare(spuId);
  }
});
</script>

<style scoped>
.compare-page { padding: 0 14px 30px; min-height: 100vh; }
.cmp-head { display: flex; align-items: center; justify-content: space-between; padding: 12px 0; }
.head-btn { width: 32px; height: 32px; display: flex; align-items: center; justify-content: center; }
.head-title { font-size: 15px; font-weight: 700; }
.head-spacer { width: 32px; }
.search-bar { display: flex; gap: 8px; margin-bottom: 14px; }
.search-bar input {
  flex: 1;
  height: 40px;
  padding: 0 14px;
  background: var(--card);
  border-radius: 999px;
  font-size: 13.5px;
  border: 1px solid var(--line);
}
.search-btn { width: 40px; height: 40px; border-radius: 50%; background: var(--brand); color: #fff; display: flex; align-items: center; justify-content: center; flex: none; }
.hero {
  background: linear-gradient(120deg, #4C5CFF, #7A6BFF);
  border-radius: var(--radius);
  padding: 18px;
  color: #fff;
  margin-bottom: 12px;
}
.hero-top { display: flex; align-items: center; justify-content: space-between; }
.hero-badge { display: inline-flex; align-items: center; gap: 5px; font-size: 11px; font-weight: 700; background: rgba(255,255,255,.2); padding: 5px 10px; border-radius: 999px; }
.hero-save { font-size: 11px; opacity: 0.9; }
.hero-price { margin-top: 12px; font-size: 15px; font-weight: 700; display: flex; align-items: baseline; gap: 6px; }
.hero-price b { font-size: 34px; letter-spacing: 0.5px; }
.hero-unit { font-size: 12px; opacity: 0.85; font-weight: 400; }
.hero-name { font-size: 13px; opacity: 0.9; margin-top: 4px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.hero-sug { font-size: 11px; opacity: 0.85; margin-top: 4px; }
.hero-btn { margin-top: 14px; background: #fff; color: var(--brand); font-size: 13px; font-weight: 700; border-radius: 999px; padding: 9px 22px; }
.card { background: var(--card); border-radius: var(--radius); padding: 16px; margin-bottom: 12px; }
.sec-title { font-size: 14px; font-weight: 700; margin-bottom: 12px; }
.cmp-row { display: flex; align-items: center; gap: 8px; padding: 10px 0; border-bottom: 1px solid var(--line); font-size: 12px; }
.cmp-row:last-of-type { border-bottom: none; }
.cmp-row.best { background: var(--brand-soft); border-radius: 10px; padding: 10px 8px; }
.cmp-name { flex: 1; font-weight: 600; }
.cmp-price b { font-size: 14px; }
.best-tag { font-style: normal; font-size: 9.5px; font-weight: 700; color: #fff; background: var(--brand); padding: 2px 6px; border-radius: 999px; margin-left: 4px; }
.cmp-ship { color: var(--ink-3); font-size: 10.5px; flex: 1.2; text-align: right; }
.cmp-go { width: 24px; height: 24px; border-radius: 8px; background: var(--bg); color: var(--ink-2); font-size: 11px; flex: none; }
.demo-note { margin-top: 10px; font-size: 10px; color: var(--ink-3); }
.save-row { display: flex; justify-content: space-between; font-size: 13px; padding: 7px 0; color: var(--ink-2); }
.save-row b { color: var(--ink); }
.save-row.total { border-top: 1px dashed var(--line-2); margin-top: 6px; padding-top: 12px; font-weight: 700; }
.save-amount { color: var(--accent) !important; font-size: 17px; }
</style>
