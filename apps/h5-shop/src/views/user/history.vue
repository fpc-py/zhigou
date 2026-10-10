<template>
  <div class="page his-page">
    <header class="his-head">
      <button class="head-btn" @click="router.back()"><Icon name="back" /></button>
      <span class="head-title">浏览历史</span>
      <span class="head-count" v-if="items.length > 0">{{ items.length }} 个商品</span>
    </header>

    <Skeleton v-if="loading" w="100%" h="88px" :repeat="3" />
    <EmptyState v-else-if="items.length === 0" illustration="🕘" text="还没有浏览记录" desc="浏览过的商品会记录在这里，方便你随时回看" />

    <template v-else>
      <div class="his-list">
        <div v-for="it in items" :key="it.spuId" class="his-item" @click="router.push(`/product/${it.spuId}`)">
          <div class="thumb">
            <img v-if="it.imageUrl" :src="it.imageUrl" :alt="it.spuName" />
            <div v-else class="thumb-ph">🛍️</div>
          </div>
          <div class="info">
            <p class="name">{{ it.spuName || '智购精选商品' }}</p>
            <p class="price" v-if="it.price != null">¥{{ formatPrice(it.price) }}</p>
            <p class="time" v-if="it.lastBrowseTime">
              <template v-if="it.browseCount && it.browseCount > 1">看过 {{ it.browseCount }} 次 · </template>
              {{ formatTime(it.lastBrowseTime) }}
            </p>
          </div>
          <Icon name="chev" class="chev" />
        </div>
      </div>
      <p class="list-end">仅展示最近 100 条浏览记录</p>
    </template>
  </div>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue';
import { useRouter } from 'vue-router';
import { recentBrowse } from '@/api/user';
import type { ProductTrackItem } from '@/api/user';
import { showToast, formatPrice } from '@/utils';
import Skeleton from '@/components/Skeleton.vue';
import EmptyState from '@/components/EmptyState.vue';
import Icon from '@/components/Icon.vue';

const router = useRouter();
const items = ref<ProductTrackItem[]>([]);
const loading = ref(true);

function formatTime(t?: string): string {
  if (!t) return '';
  return t.replace('T', ' ').slice(0, 16);
}

async function load() {
  loading.value = true;
  try {
    items.value = await recentBrowse(100);
  } catch {
    showToast('加载失败，请稍后再试');
  } finally {
    loading.value = false;
  }
}

onMounted(load);
</script>

<style scoped>
.his-page { min-height: 100vh; background: var(--bg); padding-bottom: 24px; }
.his-head {
  position: sticky;
  top: 0;
  z-index: 20;
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 10px 14px;
  background: rgba(255, 255, 255, 0.92);
  backdrop-filter: blur(8px);
}
.head-btn { width: 32px; height: 32px; display: flex; align-items: center; justify-content: center; color: var(--ink); }
.head-title { font-size: 15px; font-weight: 700; flex: 1; text-align: left; }
.head-count { font-size: 11.5px; color: var(--ink-3); }
.his-list { padding: 8px 14px; }
.his-item {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 11px 12px;
  margin-bottom: 10px;
  background: var(--card);
  border-radius: var(--radius);
  cursor: pointer;
}
.thumb { width: 64px; height: 64px; border-radius: 12px; overflow: hidden; background: #f2f4f9; flex: none; display: flex; align-items: center; justify-content: center; }
.thumb img { width: 100%; height: 100%; object-fit: cover; }
.thumb-ph { font-size: 24px; opacity: 0.4; }
.info { flex: 1; min-width: 0; }
.name { font-size: 13px; font-weight: 600; line-height: 1.4; display: -webkit-box; -webkit-line-clamp: 2; -webkit-box-orient: vertical; overflow: hidden; }
.price { color: var(--accent); font-size: 14px; font-weight: 700; margin-top: 4px; }
.time { font-size: 10.5px; color: var(--ink-3); margin-top: 3px; }
.chev { color: var(--ink-3); }
.list-end { text-align: center; font-size: 11.5px; color: var(--ink-3); padding: 12px 0 4px; }
</style>
