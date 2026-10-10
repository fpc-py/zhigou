<template>
  <div class="life-page">
    <header class="life-head">
      <h1>本地生活</h1>
      <span class="life-sub">成都 · 周边到店 · 演示数据</span>
      <button class="appt-btn" @click="router.push('/life/appointments')">我的预约</button>
    </header>

    <div class="tabs">
      <button v-for="t in tabs" :key="t.key" class="tab" :class="{ on: tab === t.key }" @click="switchTab(t.key)">
        {{ t.label }}
      </button>
    </div>

    <div v-if="loading" class="skeleton-list">
      <div v-for="i in 4" :key="i" class="sk-card"><div class="skeleton line" style="width:70%" /><div class="skeleton line" style="width:45%" /></div>
    </div>

    <div v-else-if="stores.length" class="store-list">
      <div v-for="s in stores" :key="s.id" class="store-card" @click="router.push(`/life/store/${s.id}`)">
        <div class="store-main">
          <h3>{{ s.name }}</h3>
          <p class="store-addr">{{ s.address }} · {{ s.distanceM > 1000 ? (s.distanceM / 1000).toFixed(1) + 'km' : s.distanceM + 'm' }}</p>
          <p class="store-tags" v-if="s.tags">{{ s.tags }}</p>
        </div>
        <div class="store-side">
          <span class="store-rating">★ {{ s.rating }}</span>
          <span class="store-hours">{{ s.hours || '—' }}</span>
        </div>
      </div>
    </div>

    <div v-else-if="skus.length" class="sku-list">
      <div v-for="k in skus" :key="k.id" class="sku-card">
        <div class="sku-info">
          <h3>{{ k.name }}</h3>
          <p class="sku-desc">{{ k.description || '' }}</p>
          <p class="sku-price"><b>¥{{ (k.priceFen / 100).toFixed(2) }}</b> / {{ k.unit }}</p>
        </div>
        <span class="sku-tag">{{ k.category }}</span>
      </div>
    </div>

    <div v-else class="life-empty"><p>该分类暂无内容</p><button @click="load">重新加载</button></div>
  </div>
</template>

<script lang="ts" setup>
import { ref, onMounted } from 'vue';
import { useRouter } from 'vue-router';
import { getStores, getSkus, type PoiStore, type LifeSku } from '@/api/life';

const router = useRouter();
const tabs = [
  { key: '', label: '周边推荐' },
  { key: '商圈', label: '商圈' },
  { key: '餐饮', label: '到店' },
  { key: '外卖', label: '外卖' },
  { key: '生鲜', label: '生鲜' },
  { key: '家政', label: '家政' },
];
const tab = ref('');
const stores = ref<PoiStore[]>([]);
const skus = ref<LifeSku[]>([]);
const loading = ref(true);

async function load() {
  loading.value = true;
  if (tab.value === '' || tab.value === '商圈' || tab.value === '餐饮') {
    skus.value = [];
    stores.value = await getStores(tab.value === '' ? undefined : tab.value);
  } else {
    stores.value = [];
    skus.value = await getSkus(tab.value);
  }
  loading.value = false;
}

function switchTab(k: string) {
  tab.value = k;
  load();
}

onMounted(load);
</script>

<style scoped>
.life-page { min-height: 100vh; background: #fff; }
.life-head { padding: 14px 16px 8px; display: flex; align-items: center; gap: 10px; flex-wrap: wrap; }
.life-head h1 { font-size: 20px; font-weight: 700; margin: 0; }
.life-sub { font-size: 11px; color: var(--ink-3); }
.appt-btn { margin-left: auto; font-size: 12px; color: #0d9488; border: 1px solid #0d9488; background: #fff; border-radius: 999px; padding: 5px 12px; }
.tabs { display: flex; gap: 6px; overflow-x: auto; padding: 4px 16px 10px; scrollbar-width: none; }
.tab { flex: none; padding: 6px 14px; border-radius: 999px; font-size: 13px; color: #666; background: #f5f5f5; border: none; }
.tab.on { background: #0d9488; color: #fff; font-weight: 600; }
.store-list, .sku-list { padding: 4px 16px 90px; display: flex; flex-direction: column; gap: 10px; }
.store-card { display: flex; justify-content: space-between; gap: 10px; background: #fff; border: 1px solid #f0f0f0; border-radius: 14px; padding: 12px 14px; }
.store-main h3 { margin: 0 0 4px; font-size: 15px; font-weight: 600; }
.store-addr { margin: 0 0 4px; font-size: 12px; color: var(--ink-3); }
.store-tags { margin: 0; font-size: 11px; color: #0d9488; }
.store-side { display: flex; flex-direction: column; align-items: flex-end; gap: 6px; flex: none; }
.store-rating { font-size: 13px; font-weight: 700; color: #f59e0b; }
.store-hours { font-size: 11px; color: var(--ink-3); }
.sku-card { display: flex; justify-content: space-between; align-items: flex-start; gap: 10px; border: 1px solid #f0f0f0; border-radius: 14px; padding: 12px 14px; }
.sku-info h3 { margin: 0 0 4px; font-size: 14px; font-weight: 600; }
.sku-desc { margin: 0 0 6px; font-size: 12px; color: var(--ink-3); }
.sku-price b { font-size: 15px; color: #e11d48; }
.sku-tag { flex: none; font-size: 11px; color: #0d9488; background: #e8f8f2; padding: 3px 10px; border-radius: 999px; }
.skeleton-list { padding: 4px 16px; display: flex; flex-direction: column; gap: 10px; }
.sk-card { border-radius: 14px; padding: 14px; background: #fafafa; display: flex; flex-direction: column; gap: 8px; }
.life-empty { text-align: center; padding-top: 25vh; color: var(--ink-3); }
.life-empty button { margin-top: 12px; padding: 8px 20px; border-radius: 999px; border: 0; background: #0d9488; color: #fff; }
</style>
