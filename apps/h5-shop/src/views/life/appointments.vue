<template>
  <div class="ap-page">
    <header class="ap-head">
      <button class="back" @click="router.back()">←</button>
      <h1>我的预约</h1>
    </header>

    <div v-if="list.length" class="ap-list">
      <div v-for="a in list" :key="a.id" class="ap-card">
        <div class="ap-info">
          <h3>{{ storeName(a.storeId) }}</h3>
          <p class="ap-time">{{ a.appointmentTime }}</p>
          <p class="ap-remark" v-if="a.remark">{{ a.remark }}</p>
        </div>
        <div class="ap-side">
          <span class="ap-status" :class="'s' + a.status">{{ statusText(a.status) }}</span>
          <button v-if="a.status !== 3 && a.status !== 2" class="cancel" @click="cancel(a.id)">取消</button>
        </div>
      </div>
    </div>
    <div v-else class="ap-empty"><p>暂无预约</p><button @click="router.push('/life')">去逛逛</button></div>
  </div>
</template>

<script lang="ts" setup>
import { ref, onMounted } from 'vue';
import { useRouter } from 'vue-router';
import { getMyAppointments, cancelAppointment, getStores, type LifeAppointment, type PoiStore } from '@/api/life';

const router = useRouter();
const list = ref<LifeAppointment[]>([]);
const storeMap = ref<Record<string, string>>({});

function statusText(s: number) {
  return s === 0 ? '待确认' : s === 1 ? '已确认' : s === 2 ? '已完成' : '已取消';
}

function storeName(id: string) {
  return storeMap.value[id] || '门店 ' + id.slice(-4);
}

async function load() {
  list.value = await getMyAppointments();
  const stores = await getStores();
  stores.forEach((s: PoiStore) => (storeMap.value[s.id] = s.name));
}

async function cancel(id: string) {
  const ok = await cancelAppointment(id);
  if (ok) load();
}

onMounted(load);
</script>

<style scoped>
.ap-page { min-height: 100vh; background: #fff; }
.ap-head { display: flex; align-items: center; gap: 10px; padding: 14px 16px 4px; }
.back { border: 0; background: none; font-size: 18px; }
.ap-head h1 { margin: 0; font-size: 19px; font-weight: 700; }
.ap-list { padding: 10px 16px 90px; display: flex; flex-direction: column; gap: 10px; }
.ap-card { display: flex; justify-content: space-between; gap: 10px; border: 1px solid #f0f0f0; border-radius: 14px; padding: 12px 14px; }
.ap-info h3 { margin: 0 0 4px; font-size: 14px; font-weight: 600; }
.ap-time { margin: 0 0 4px; font-size: 12px; color: var(--ink-3); }
.ap-remark { margin: 0; font-size: 11px; color: var(--ink-3); }
.ap-side { display: flex; flex-direction: column; align-items: flex-end; gap: 8px; flex: none; }
.ap-status { font-size: 12px; font-weight: 600; padding: 3px 10px; border-radius: 999px; }
.ap-status.s0 { background: #fef3c7; color: #b45309; }
.ap-status.s1 { background: #d1fae5; color: #047857; }
.ap-status.s2 { background: #e5e7eb; color: #6b7280; }
.ap-status.s3 { background: #fee2e2; color: #b91c1c; }
.cancel { border: 1px solid #e5e7eb; background: #fff; color: #6b7280; border-radius: 999px; padding: 4px 14px; font-size: 12px; }
.ap-empty { text-align: center; padding-top: 30vh; color: var(--ink-3); }
.ap-empty button { margin-top: 12px; padding: 8px 20px; border-radius: 999px; border: 0; background: #0d9488; color: #fff; }
</style>
