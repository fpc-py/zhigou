<template>
  <div class="page as-detail-page">
    <header class="page-head">
      <button class="head-btn" @click="router.back()"><Icon name="back" /></button>
      <span class="head-title">售后详情</span>
      <span class="head-spacer" />
    </header>

    <Skeleton v-if="loading" w="100%" h="120px" :repeat="3" />
    <EmptyState v-else-if="!ao" illustration="📋" text="售后单不存在" />

    <template v-else>
      <!-- 状态卡 -->
      <section class="status-hero" :class="statusClass(ao.status)">
        <p class="status-title">{{ statusText(ao.status) }}</p>
        <p class="status-sub">{{ statusSub(ao.status) }}</p>
      </section>

      <!-- 售后信息 -->
      <section class="card">
        <h3 class="sec-title">售后信息</h3>
        <div class="info-row"><span>售后单号</span><b>{{ ao.aftersaleNo }}</b></div>
        <div class="info-row"><span>关联订单</span><b>{{ ao.orderNo }}</b></div>
        <div class="info-row"><span>售后类型</span><b>{{ typeText(ao.type) }}</b></div>
        <div class="info-row"><span>退款金额</span><b class="pay">¥{{ formatPrice(ao.amount) }}</b></div>
        <div v-if="ao.refundNo" class="info-row"><span>退款单号</span><b>{{ ao.refundNo }}</b></div>
        <div class="info-row"><span>申请时间</span><b>{{ ao.applyAt }}</b></div>
        <div v-if="ao.rejectReason" class="info-row"><span>拒绝原因</span><b class="danger">{{ ao.rejectReason }}</b></div>
      </section>

      <!-- 原因 -->
      <section v-if="ao.reason" class="card">
        <h3 class="sec-title">申请原因</h3>
        <p class="reason-text">{{ ao.reason }}</p>
      </section>

      <!-- 操作 -->
      <div class="as-ops">
        <button v-if="ao.status === 'APPLYING'" class="op line" @click="cancel">取消申请</button>
        <button v-if="ao.status === 'APPLYING'" class="op brand" @click="router.push({ path: `/order/${ao.orderNo}` })">查看订单</button>
      </div>
    </template>
  </div>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { getAftersaleDetail, cancelAftersale } from '@/api/aftersale';
import type { AftersaleOrder } from '@/api/aftersale';
import { showToast, formatPrice } from '@/utils';
import Icon from '@/components/Icon.vue';
import Skeleton from '@/components/Skeleton.vue';
import EmptyState from '@/components/EmptyState.vue';

const route = useRoute();
const router = useRouter();
const no = route.params.no as string;
const loading = ref(true);
const ao = ref<AftersaleOrder | null>(null);

function statusText(s: string) {
  const map: Record<string, string> = {
    APPLYING: '待商家审核', SELLER_APPROVED: '审核通过，待退款', REFUNDING: '退款处理中',
    REFUNDED: '已退款', REJECTED: '已拒绝', CANCELED: '已取消',
  };
  return map[s] ?? s;
}

function statusSub(s: string) {
  const map: Record<string, string> = {
    APPLYING: '商家将在 1-3 个工作日内审核',
    SELLER_APPROVED: '退款将原路退回，请耐心等待',
    REFUNDING: '资金正在原路退回中',
    REFUNDED: '退款已完成，请查收',
    REJECTED: '如有疑问可联系客服申诉',
    CANCELED: '售后申请已取消',
  };
  return map[s] ?? '';
}

function statusClass(s: string) {
  if (s === 'APPLYING' || s === 'SELLER_APPROVED' || s === 'REFUNDING') return 'hero-mid';
  if (s === 'REFUNDED') return 'hero-ok';
  return 'hero-close';
}

function typeText(t: string) {
  const map: Record<string, string> = { REFUND: '仅退款', RETURN_REFUND: '退货退款' };
  return map[t] ?? t;
}

async function load() {
  loading.value = true;
  try {
    ao.value = await getAftersaleDetail(no);
  } catch {
    /* 已提示 */
  } finally {
    loading.value = false;
  }
}

async function cancel() {
  try {
    await cancelAftersale(no);
    showToast('售后申请已取消');
    load();
  } catch {
    /* 已提示 */
  }
}

onMounted(load);
</script>

<style scoped>
.as-detail-page { min-height: 100vh; padding: 0 14px 120px; }
.page-head { display: flex; align-items: center; justify-content: space-between; padding: 12px 0; }
.head-btn { width: 32px; height: 32px; display: flex; align-items: center; justify-content: center; }
.head-title { font-size: 15px; font-weight: 700; }
.head-spacer { width: 32px; }
.status-hero { border-radius: var(--radius); padding: 20px 18px; color: #fff; margin-bottom: 12px; }
.hero-mid { background: linear-gradient(120deg, #4C5CFF, #7A6BFF); }
.hero-ok { background: linear-gradient(120deg, #00A87E, #2FC7A0); }
.hero-close { background: linear-gradient(120deg, #8B90A0, #B6BAC7); }
.status-title { font-size: 20px; font-weight: 800; }
.status-sub { font-size: 12px; opacity: 0.9; margin-top: 5px; }
.card { background: var(--card); border-radius: var(--radius); padding: 14px 16px; margin-bottom: 12px; }
.sec-title { font-size: 14px; font-weight: 700; margin-bottom: 10px; }
.info-row { display: flex; justify-content: space-between; font-size: 12.5px; color: var(--ink-2); padding: 5px 0; gap: 12px; }
.info-row b { color: var(--ink); font-weight: 600; word-break: break-all; text-align: right; }
.info-row .pay { color: var(--accent); font-weight: 700; }
.info-row .danger { color: #E64340; }
.reason-text { font-size: 13px; color: var(--ink-2); line-height: 1.6; }
.as-ops { position: fixed; bottom: 0; left: 50%; transform: translateX(-50%); width: 100%; max-width: 414px; display: flex; gap: 10px; padding: 12px 16px calc(12px + env(safe-area-inset-bottom, 0px)); background: rgba(255,255,255,.97); border-top: 1px solid var(--line); }
.op { flex: 1; height: 42px; border-radius: 999px; font-size: 13.5px; font-weight: 600; }
.op.line { color: var(--ink-2); border: 1px solid var(--line-2); }
.op.brand { background: var(--brand); color: #fff; }
</style>
