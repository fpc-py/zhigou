<template>
  <div class="page fav-page">
    <header class="fav-head">
      <button class="head-btn" @click="router.back()"><Icon name="back" /></button>
      <span class="head-title">我的收藏</span>
      <span class="head-count" v-if="total > 0">{{ total }} 件</span>
    </header>

    <Skeleton v-if="loading" w="100%" h="96px" :repeat="3" />
    <EmptyState v-else-if="items.length === 0" illustration="🫶" text="还没有收藏商品" desc="在商品详情页点击「收藏」，喜欢的商品会出现在这里" />

    <template v-else>
      <div class="fav-list">
        <div v-for="it in items" :key="it.spuId" class="fav-item" @click="router.push(`/product/${it.spuId}`)">
          <div class="thumb">
            <img v-if="it.imageUrl" :src="it.imageUrl" :alt="it.spuName" />
            <div v-else class="thumb-ph">🛍️</div>
          </div>
          <div class="info">
            <p class="name">{{ it.spuName || '智购精选商品' }}</p>
            <p class="price" v-if="it.price != null">¥{{ formatPrice(it.price) }}</p>
            <p class="time" v-if="it.createTime">{{ formatTime(it.createTime) }} 收藏</p>
          </div>
          <button class="unfav" @click.stop="unfav(it.spuId)"><Icon name="heart" :class="{ faved: true }" />取消</button>
        </div>
      </div>

      <p v-if="!hasMore && items.length > 0" class="list-end">已展示全部收藏</p>
      <button v-else-if="hasMore" class="load-more" @click="loadMore">加载更多</button>
    </template>
  </div>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue';
import { useRouter } from 'vue-router';
import { listFavorites, removeFavorite } from '@/api/user';
import type { ProductTrackItem } from '@/api/user';
import { showToast, formatPrice } from '@/utils';
import Skeleton from '@/components/Skeleton.vue';
import EmptyState from '@/components/EmptyState.vue';
import Icon from '@/components/Icon.vue';

const router = useRouter();
const items = ref<ProductTrackItem[]>([]);
const total = ref(0);
const page = ref(1);
const loading = ref(true);
const hasMore = ref(false);

function formatTime(t?: string): string {
  if (!t) return '';
  return t.replace('T', ' ').slice(0, 16);
}

async function load(pageNo: number) {
  loading.value = true;
  try {
    const data = await listFavorites(pageNo, 10);
    if (pageNo === 1) items.value = data.records;
    else items.value = items.value.concat(data.records);
    total.value = data.total;
    hasMore.value = items.value.length < data.total;
  } catch {
    showToast('加载失败，请稍后再试');
  } finally {
    loading.value = false;
  }
}

function loadMore() {
  page.value += 1;
  load(page.value);
}

async function unfav(spuId: string) {
  try {
    await removeFavorite(spuId);
    items.value = items.value.filter((it) => it.spuId !== spuId);
    total.value -= 1;
    hasMore.value = items.value.length < total.value;
    showToast('已取消收藏');
  } catch {
    showToast('操作失败，请稍后再试');
  }
}

onMounted(() => load(1));
</script>

<style scoped>
.fav-page { min-height: 100vh; background: var(--bg); padding-bottom: 24px; }
.fav-head {
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
.fav-list { padding: 8px 14px; }
.fav-item {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 12px;
  margin-bottom: 10px;
  background: var(--card);
  border-radius: var(--radius);
  cursor: pointer;
}
.thumb { width: 72px; height: 72px; border-radius: 12px; overflow: hidden; background: #f2f4f9; flex: none; display: flex; align-items: center; justify-content: center; }
.thumb img { width: 100%; height: 100%; object-fit: cover; }
.thumb-ph { font-size: 26px; opacity: 0.4; }
.info { flex: 1; min-width: 0; }
.name { font-size: 13.5px; font-weight: 600; line-height: 1.4; display: -webkit-box; -webkit-line-clamp: 2; -webkit-box-orient: vertical; overflow: hidden; }
.price { color: var(--accent); font-size: 15px; font-weight: 700; margin-top: 4px; }
.time { font-size: 10.5px; color: var(--ink-3); margin-top: 3px; }
.unfav {
  display: flex;
  align-items: center;
  gap: 3px;
  font-size: 11px;
  color: var(--ink-3);
  padding: 8px;
}
.unfav .faved { color: var(--accent); fill: var(--accent); }
.list-end { text-align: center; font-size: 11.5px; color: var(--ink-3); padding: 12px 0 4px; }
.load-more {
  display: block;
  margin: 6px auto 0;
  font-size: 13px;
  color: var(--brand);
  background: var(--card);
  border-radius: 999px;
  padding: 10px 26px;
}
</style>
