<template>
  <div class="page as-apply-page">
    <header class="page-head">
      <button class="head-btn" @click="router.back()"><Icon name="back" /></button>
      <span class="head-title">申请售后</span>
      <span class="head-spacer" />
    </header>

    <Skeleton v-if="loading" w="100%" h="120px" :repeat="3" />
    <EmptyState v-else-if="!order" illustration="📦" text="订单不存在或不可售后" />

    <template v-else>
      <!-- 商品选择 -->
      <section class="card">
        <h3 class="sec-title">选择商品</h3>
        <div v-for="it in order.items" :key="it.skuId"
             class="good-row" :class="{ picked: pickedSku === it.skuId }" @click="pick(it)">
          <div class="good-ph">{{ it.skuName?.slice(0, 1) || '货' }}</div>
          <div class="good-info">
            <p class="good-name">{{ it.skuName || '商品' }}</p>
            <p class="good-spec">SKU {{ it.skuId }} · x{{ it.count }}</p>
          </div>
          <div class="good-right">
            <p class="good-price">¥{{ formatPrice(it.price) }}</p>
            <span v-if="pickedSku === it.skuId" class="pick-badge">✓</span>
          </div>
        </div>
      </section>

      <!-- 售后类型 -->
      <section class="card">
        <h3 class="sec-title">售后类型</h3>
        <div class="type-grid">
          <button v-for="t in types" :key="t.value" class="type-btn"
                  :class="{ active: form.type === t.value }" @click="form.type = t.value">
            {{ t.label }}
          </button>
        </div>
      </section>

      <!-- 退款金额 -->
      <section class="card">
        <h3 class="sec-title">退款金额</h3>
        <div class="amount-row">
          <span class="amount-symbol">¥</span>
          <input v-model.number="form.amount" class="amount-input" type="number" min="0" />
          <button class="max-btn" @click="form.amount = maxAmount">全额</button>
        </div>
        <p class="amount-hint">实付 {{ formatPrice(order.payAmount) }}，最多可退 {{ formatPrice(maxAmount) }}</p>
      </section>

      <!-- 原因 -->
      <section class="card">
        <h3 class="sec-title">申请原因</h3>
        <textarea v-model="form.reason" class="reason-input" rows="3" maxlength="200"
                  placeholder="请描述申请售后的原因（选填）"></textarea>
      </section>

      <!-- 提交 -->
      <div class="submit-bar">
        <button class="submit-btn" :disabled="submitting" @click="submit">提交申请</button>
      </div>
    </template>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { getOrderDetail } from '@/api/order';
import type { OrderDetail, OrderItem } from '@/api/order';
import { applyAftersale } from '@/api/aftersale';
import { showToast, formatPrice } from '@/utils';
import Icon from '@/components/Icon.vue';
import Skeleton from '@/components/Skeleton.vue';
import EmptyState from '@/components/EmptyState.vue';

const route = useRoute();
const router = useRouter();
const orderId = route.query.orderId as string;
const loading = ref(true);
const submitting = ref(false);
const order = ref<OrderDetail | null>(null);
const pickedSku = ref<string | null>(null);
const pickedCount = ref(0);

const types = [
  { value: 'REFUND', label: '仅退款' },
  { value: 'RETURN_REFUND', label: '退货退款' },
];

const form = ref({ type: 'REFUND', amount: 0, reason: '' });

const maxAmount = computed(() => order.value?.payAmount ?? 0);

function pick(it: OrderItem) {
  pickedSku.value = it.skuId;
  pickedCount.value = it.count;
}

async function load() {
  if (!orderId) return;
  loading.value = true;
  try {
    order.value = await getOrderDetail(orderId);
    const first = order.value?.items?.[0];
    if (first) pick(first);
    form.value.amount = order.value?.payAmount ?? 0;
  } catch {
    /* 已提示 */
  } finally {
    loading.value = false;
  }
}

async function submit() {
  if (!order.value) return;
  if (form.value.amount <= 0 || form.value.amount > maxAmount.value) {
    showToast('退款金额不合法');
    return;
  }
  submitting.value = true;
  try {
    const ao = await applyAftersale({
      orderNo: orderId,
      type: form.value.type,
      reason: form.value.reason || undefined,
      amount: form.value.amount,
      skuId: pickedSku.value ? Number(pickedSku.value) : null,
      count: pickedSku.value ? pickedCount.value : null,
    });
    showToast('售后申请已提交');
    router.replace({ path: `/aftersale/${ao.aftersaleNo}` });
  } catch {
    /* 已提示 */
  } finally {
    submitting.value = false;
  }
}

onMounted(load);
</script>

<style scoped>
.as-apply-page { min-height: 100vh; padding: 0 14px 120px; }
.page-head { display: flex; align-items: center; justify-content: space-between; padding: 12px 0; }
.head-btn { width: 32px; height: 32px; display: flex; align-items: center; justify-content: center; }
.head-title { font-size: 15px; font-weight: 700; }
.head-spacer { width: 32px; }
.card { background: var(--card); border-radius: var(--radius); padding: 14px 16px; margin-bottom: 12px; }
.sec-title { font-size: 14px; font-weight: 700; margin-bottom: 10px; }
.good-row { display: flex; align-items: center; gap: 10px; padding: 9px 0; border-radius: 10px; }
.good-row.picked { background: var(--brand-soft); }
.good-ph { width: 46px; height: 46px; border-radius: 10px; background: var(--brand-soft); color: var(--brand); font-size: 18px; font-weight: 700; display: flex; align-items: center; justify-content: center; flex: none; }
.good-info { flex: 1; min-width: 0; }
.good-name { font-size: 13px; font-weight: 600; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.good-spec { font-size: 10.5px; color: var(--ink-3); margin-top: 2px; }
.good-right { text-align: right; display: flex; align-items: center; gap: 6px; }
.good-price { color: var(--accent); font-size: 12px; font-weight: 700; }
.pick-badge { color: var(--brand); font-weight: 800; font-size: 15px; }
.type-grid { display: flex; gap: 10px; }
.type-btn { flex: 1; height: 40px; border-radius: 999px; border: 1px solid var(--line-2); font-size: 13px; font-weight: 600; color: var(--ink-2); }
.type-btn.active { background: var(--brand); border-color: var(--brand); color: #fff; }
.amount-row { display: flex; align-items: center; gap: 8px; }
.amount-symbol { font-size: 18px; font-weight: 800; color: var(--accent); }
.amount-input { flex: 1; font-size: 22px; font-weight: 800; color: var(--accent); border: none; outline: none; background: transparent; }
.max-btn { height: 32px; padding: 0 14px; border-radius: 999px; background: var(--brand-soft); color: var(--brand); font-size: 12px; font-weight: 600; }
.amount-hint { font-size: 11px; color: var(--ink-3); margin-top: 8px; }
.reason-input { width: 100%; border: 1px solid var(--line-2); border-radius: 10px; padding: 10px 12px; font-size: 13px; resize: none; background: transparent; color: var(--ink); box-sizing: border-box; }
.submit-bar { position: fixed; bottom: 0; left: 50%; transform: translateX(-50%); width: 100%; max-width: 414px; padding: 12px 16px calc(12px + env(safe-area-inset-bottom, 0px)); background: rgba(255,255,255,.97); border-top: 1px solid var(--line); }
.submit-btn { width: 100%; height: 44px; border-radius: 999px; background: var(--brand); color: #fff; font-size: 15px; font-weight: 700; }
.submit-btn:disabled { opacity: 0.5; }
</style>
