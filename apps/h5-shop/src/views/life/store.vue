<template>
  <div class="st-page">
    <header class="st-head">
      <button class="back" @click="router.back()">←</button>
      <h1 v-if="detail">{{ detail.store.name }}</h1>
      <h1 v-else>门店</h1>
    </header>

    <div v-if="detail">
      <div class="st-info">
        <p class="st-addr">{{ detail.store.address }}</p>
        <p class="st-meta">★ {{ detail.store.rating }} · {{ detail.store.hours || '营业中' }} · {{ detail.store.distanceM > 1000 ? (detail.store.distanceM / 1000).toFixed(1) + 'km' : detail.store.distanceM + 'm' }}</p>
        <p class="st-tags" v-if="detail.store.tags">{{ detail.store.tags }}</p>
      </div>

      <h3 class="sec-title">服务项目</h3>
      <div class="svc-list">
        <div v-for="k in detail.skus" :key="k.id" class="svc-card">
          <div class="svc-info">
            <h4>{{ k.name }}</h4>
            <p>{{ k.description || '' }}</p>
            <p class="svc-price"><b>¥{{ (k.priceFen / 100).toFixed(2) }}</b> / {{ k.unit }}</p>
          </div>
          <button class="book-btn" @click="openBook(k)">预约</button>
        </div>
      </div>

      <p class="demo-note">演示环境：到店/服务为占位流程，提交后写入预约单（状态 待确认）</p>
    </div>
    <div v-else class="st-empty"><p>门店不存在</p><button @click="router.back()">返回</button></div>

    <!-- 预约弹层 -->
    <div class="book-mask" v-if="bookSku" @click.self="bookSku = null">
      <div class="book-panel">
        <h4>预约「{{ bookSku.name }}」</h4>
        <input v-model="bookTime" placeholder="预约时间，如 2026-10-12 18:30" />
        <input v-model="bookRemark" placeholder="备注（选填）" />
        <button class="submit" :disabled="!bookTime.trim()" @click="submitBook">提交预约</button>
      </div>
    </div>
  </div>
</template>

<script lang="ts" setup>
import { ref, onMounted } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { getStoreDetail, createAppointment, type LifeSku } from '@/api/life';

const route = useRoute();
const router = useRouter();
const detail = ref<{ store: any; skus: LifeSku[] } | null>(null);
const bookSku = ref<LifeSku | null>(null);
const bookTime = ref('');
const bookRemark = ref('');

async function load() {
  detail.value = await getStoreDetail(String(route.params.id));
}

function openBook(k: LifeSku) {
  bookSku.value = k;
  bookTime.value = '';
  bookRemark.value = '';
}

async function submitBook() {
  if (!bookSku.value || !bookTime.value.trim()) return;
  const ok = await createAppointment({
    storeId: String(route.params.id),
    skuId: bookSku.value.id,
    appointmentTime: bookTime.value.trim(),
    remark: bookRemark.value.trim() || undefined,
  });
  if (ok) {
    bookSku.value = null;
    router.push('/life/appointments');
  }
}

onMounted(load);
</script>

<style scoped>
.st-page { min-height: 100vh; background: #fff; }
.st-head { display: flex; align-items: center; gap: 10px; padding: 14px 16px 4px; }
.back { border: 0; background: none; font-size: 18px; color: var(--ink-1); }
.st-head h1 { margin: 0; font-size: 19px; font-weight: 700; }
.st-info { padding: 6px 16px 2px; }
.st-addr { margin: 0 0 4px; font-size: 13px; color: var(--ink-2); }
.st-meta { margin: 0 0 4px; font-size: 12px; color: var(--ink-3); }
.st-tags { margin: 0; font-size: 11px; color: #0d9488; }
.sec-title { padding: 14px 16px 6px; margin: 0; font-size: 15px; font-weight: 600; }
.svc-list { padding: 4px 16px 90px; display: flex; flex-direction: column; gap: 10px; }
.svc-card { display: flex; justify-content: space-between; align-items: center; gap: 12px; border: 1px solid #f0f0f0; border-radius: 14px; padding: 12px 14px; }
.svc-info h4 { margin: 0 0 4px; font-size: 14px; font-weight: 600; }
.svc-info p { margin: 0 0 4px; font-size: 12px; color: var(--ink-3); }
.svc-price b { font-size: 15px; color: #e11d48; }
.book-btn { flex: none; border: 0; background: #0d9488; color: #fff; border-radius: 999px; padding: 8px 20px; font-size: 14px; }
.demo-note { margin: 8px 16px 0; font-size: 11px; color: #b45309; background: #fef3c7; border-radius: 10px; padding: 8px 12px; }
.st-empty { text-align: center; padding-top: 30vh; color: var(--ink-3); }
.book-mask { position: fixed; inset: 0; background: rgba(0,0,0,.5); display: flex; align-items: flex-end; z-index: 20; }
.book-panel { width: 100%; background: #fff; border-radius: 16px 16px 0 0; padding: 18px 16px 24px; }
.book-panel h4 { margin: 0 0 14px; font-size: 15px; }
.book-panel input { width: 100%; box-sizing: border-box; border: 1px solid #e5e7eb; border-radius: 10px; padding: 10px 12px; margin-bottom: 10px; font-size: 14px; }
.submit { width: 100%; border: 0; background: #0d9488; color: #fff; border-radius: 999px; padding: 12px; font-size: 15px; font-weight: 600; }
.submit:disabled { opacity: .4; }
</style>
