<template>
  <div class="page coupons-page">
    <header class="page-head">
      <button class="head-btn" @click="router.back()"><Icon name="back" /></button>
      <span class="head-title">我的优惠券</span>
      <span class="head-spacer" />
    </header>

    <!-- 领券中心 -->
    <section class="claim-sec">
      <h2 class="claim-title">领券中心 <span class="claim-hint">每人每券限领，先到先得</span></h2>
      <div v-for="t in templates" :key="t.id" class="claim-row">
        <div class="claim-info">
          <span class="claim-badge">{{ t.badge }}</span>
          <div>
            <p class="claim-name">{{ t.title }}</p>
            <p class="claim-rule">{{ t.rule }}</p>
          </div>
        </div>
        <button class="claim-btn" :disabled="claiming === t.id" @click="claim(t.id)">
          {{ claiming === t.id ? '领取中…' : '领取' }}
        </button>
      </div>
    </section>

    <div class="tabs">
      <button v-for="t in tabs" :key="t.key" class="tab" :class="{ on: tab === t.key }" @click="switchTab(t.key)">{{ t.label }}</button>
    </div>

    <Skeleton v-if="loading" w="100%" h="90px" :repeat="2" />
    <EmptyState v-else-if="list.length === 0" illustration="🎫" text="暂无优惠券" />

    <div v-else class="coupon-list">
      <div v-for="c in list" :key="c.id" class="coupon card" :class="{ disabled: c.status !== 'UNUSED' }">
        <div class="coupon-left">
          <p class="coupon-amount">{{ templateName(c.couponTemplateId) }}</p>
          <p class="coupon-no">#{{ c.couponTemplateId }}</p>
        </div>
        <div class="coupon-right">
          <p class="coupon-status">{{ statusText(c.status) }}</p>
          <p class="coupon-date">领取于 {{ formatTime(c.createTime) }}</p>
        </div>
      </div>
    </div>

    <div v-if="toast" class="toast">{{ toast }}</div>
  </div>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue';
import { useRouter } from 'vue-router';
import { getMyCoupons, claimCoupon, COUPON_TEMPLATES } from '@/api/marketing';
import type { UserCoupon } from '@/api/marketing';
import Icon from '@/components/Icon.vue';
import Skeleton from '@/components/Skeleton.vue';
import EmptyState from '@/components/EmptyState.vue';

const router = useRouter();
const tab = ref<'UNUSED' | 'USED' | 'ALL'>('UNUSED');
const tabs = [
  { key: 'UNUSED', label: '可用' },
  { key: 'USED', label: '已用' },
  { key: 'ALL', label: '全部' },
] as const;
const loading = ref(true);
const list = ref<UserCoupon[]>([]);
const templates = COUPON_TEMPLATES;
const claiming = ref('');
const toast = ref('');

function flash(msg: string) {
  toast.value = msg;
  setTimeout(() => (toast.value = ''), 2000);
}

function templateName(templateId: string) {
  return COUPON_TEMPLATES.find((t) => t.id === templateId)?.title ?? '优惠券';
}

async function load() {
  loading.value = true;
  try {
    list.value = await getMyCoupons(tab.value === 'ALL' ? undefined : tab.value);
  } catch {
    /* 已提示 */
  } finally {
    loading.value = false;
  }
}

function switchTab(key: typeof tab.value) {
  tab.value = key;
  load();
}

async function claim(templateId: string) {
  claiming.value = templateId;
  try {
    await claimCoupon(templateId);
    flash('领取成功，已放入我的券包');
    await load();
  } catch (e) {
    flash(e instanceof Error ? e.message : '领取失败');
  } finally {
    claiming.value = '';
  }
}

function statusText(status: string) {
  const map: Record<string, string> = { UNUSED: '可使用', USED: '已使用', LOCKED: '使用中', EXPIRED: '已过期' };
  return map[status] ?? status;
}

function formatTime(t?: string) {
  if (!t) return '-';
  return t.slice(0, 10);
}

onMounted(load);
</script>

<style scoped>
.coupons-page { min-height: 100vh; padding: 0 14px 30px; }
.page-head { display: flex; align-items: center; justify-content: space-between; padding: 12px 0; }
.head-btn { width: 32px; height: 32px; display: flex; align-items: center; justify-content: center; }
.head-title { font-size: 15px; font-weight: 700; }
.head-spacer { width: 32px; }
.claim-sec { background: linear-gradient(120deg, #fff, #F6F7FF); border: 1px solid var(--line); border-radius: var(--radius); padding: 14px; margin-bottom: 14px; }
.claim-title { font-size: 14px; font-weight: 700; margin-bottom: 10px; display: flex; align-items: center; gap: 6px; }
.claim-hint { font-size: 11px; color: var(--ink-3); font-weight: 400; }
.claim-row { display: flex; align-items: center; justify-content: space-between; gap: 10px; padding: 10px 0; border-bottom: 1px dashed var(--line); }
.claim-row:last-child { border-bottom: none; }
.claim-info { display: flex; align-items: center; gap: 10px; }
.claim-badge { flex: none; font-size: 11px; font-weight: 700; color: #fff; background: var(--accent); border-radius: 8px; padding: 4px 8px; }
.claim-name { font-size: 13.5px; font-weight: 700; }
.claim-rule { font-size: 11px; color: var(--ink-3); margin-top: 2px; }
.claim-btn { flex: none; border: none; background: var(--brand); color: #fff; border-radius: 999px; padding: 7px 20px; font-size: 12.5px; font-weight: 700; }
.claim-btn:disabled { opacity: 0.6; }
.tabs { display: flex; gap: 22px; margin-bottom: 12px; }
.tab { font-size: 14px; font-weight: 600; color: var(--ink-3); padding-bottom: 6px; border-bottom: 2px solid transparent; }
.tab.on { color: var(--ink); border-bottom-color: var(--brand); }
.coupon {
  display: flex;
  align-items: center;
  gap: 14px;
  padding: 16px;
  border-radius: var(--radius);
  margin-bottom: 10px;
  background: linear-gradient(120deg, #fff, #F6F7FF);
  border: 1px solid var(--line);
}
.coupon.disabled { opacity: 0.55; filter: grayscale(0.4); }
.coupon-left { flex: 1; }
.coupon-amount { font-size: 16px; font-weight: 800; color: var(--accent); }
.coupon-no { font-size: 11px; color: var(--ink-3); margin-top: 4px; }
.coupon-right { text-align: right; }
.coupon-status { font-size: 12.5px; font-weight: 600; color: var(--brand); }
.coupon-date { font-size: 10.5px; color: var(--ink-3); margin-top: 4px; }
.toast { position: fixed; left: 50%; bottom: 60px; transform: translateX(-50%); background: rgba(0,0,0,.78); color: #fff; padding: 9px 18px; border-radius: 999px; font-size: 13px; z-index: 99; white-space: nowrap; }
</style>
