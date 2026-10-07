<template>
  <div class="page orders-page">
    <header class="page-head">
      <button class="head-btn" @click="router.back()"><Icon name="back" /></button>
      <span class="head-title">我的订单</span>
      <span class="head-spacer" />
    </header>

    <!-- 状态筛选 -->
    <div class="tabs">
      <button v-for="t in tabs" :key="t.key" class="tab" :class="{ on: activeTab === t.key }" @click="switchTab(t.key)">{{ t.label }}</button>
    </div>

    <Skeleton v-if="loading" w="100%" h="100px" :repeat="3" />
    <EmptyState v-else-if="list.length === 0" illustration="📦" text="暂无相关订单" />

    <div v-else class="order-list">
      <div v-for="o in list" :key="o.orderId" class="order card">
        <div class="order-head" @click="goDetail(o)">
          <span class="order-no">订单 {{ o.orderId }}</span>
          <span class="order-status" :class="statusClass(o.orderStatus)">{{ statusText(o.orderStatus) }}</span>
        </div>
        <div v-for="it in o.items" :key="it.skuId" class="order-item" @click="goDetail(o)">
          <div class="item-ph">{{ it.skuName?.slice(0, 1) || '货' }}</div>
          <div class="item-info">
            <p class="item-name">{{ it.skuName || '商品' }}</p>
            <p class="item-spec">SKU {{ it.skuId }}</p>
          </div>
          <div class="item-right">
            <p class="item-price">¥{{ formatPrice(it.price) }}</p>
            <p class="item-count">x{{ it.count }}</p>
          </div>
        </div>
        <div class="order-foot">
          <p class="order-amount">实付 <b>¥{{ formatPrice(o.payAmount) }}</b></p>
          <div class="order-ops">
            <button v-if="o.orderStatus === 'INIT'" class="op line" @click="cancel(o)">取消</button>
            <button v-if="o.orderStatus === 'INIT'" class="op brand" @click="pay(o)">去支付</button>
            <button v-else class="op line" @click="goDetail(o)">查看详情</button>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { getOrderMine, cancelOrder } from '@/api/order';
import type { OrderDetail } from '@/api/order';
import { createPayment, mockPay } from '@/api/payment';
import { showToast, formatPrice } from '@/utils';
import Icon from '@/components/Icon.vue';
import Skeleton from '@/components/Skeleton.vue';
import EmptyState from '@/components/EmptyState.vue';

const route = useRoute();
const router = useRouter();
const loading = ref(true);
const list = ref<OrderDetail[]>([]);
const activeTab = ref('ALL');

const tabs = [
  { key: 'ALL', label: '全部' },
  { key: 'INIT', label: '待付款' },
  { key: 'PAID', label: '待发货' },
  { key: 'SHIPPED', label: '待收货' },
  { key: 'COMPLETED', label: '已完成' },
];

const filtered = computed(() => (activeTab.value === 'ALL' ? list.value : list.value.filter((o) => o.orderStatus === activeTab.value)));

function statusText(s: string) {
  const map: Record<string, string> = {
    INIT: '待付款', PAID: '待发货', SHIPPED: '待收货', COMPLETED: '已完成',
    CLOSED: '已关闭', REFUNDING: '退款中', REFUNDED: '已退款',
  };
  return map[s] ?? s;
}

function statusClass(s: string) {
  if (s === 'INIT') return 'st-init';
  if (s === 'PAID' || s === 'SHIPPED') return 'st-mid';
  if (s === 'COMPLETED') return 'st-ok';
  return 'st-close';
}

async function load() {
  loading.value = true;
  try {
    list.value = await getOrderMine();
  } catch {
    /* 已提示 */
  } finally {
    loading.value = false;
  }
}

function switchTab(key: string) {
  activeTab.value = key;
}

function goDetail(o: OrderDetail) {
  router.push(`/order/${o.orderId}`);
}

async function cancel(o: OrderDetail) {
  try {
    await cancelOrder(String(o.orderId));
    showToast('订单已取消');
    load();
  } catch {
    /* 已提示 */
  }
}

async function pay(o: OrderDetail) {
  try {
    const pay = await createPayment(String(o.orderId), o.payAmount ?? o.totalAmount);
    if (pay?.paymentNo) {
      await mockPay(pay.paymentNo);
      showToast('支付成功');
      load();
    } else {
      showToast('支付创建失败');
    }
  } catch {
    showToast('支付失败，请重试');
  }
}

onMounted(() => {
  const status = route.query.status as string | undefined;
  if (status && tabs.some((t) => t.key === status)) activeTab.value = status;
  load();
});
</script>

<style scoped>
.orders-page { min-height: 100vh; padding: 0 14px 30px; }
.page-head { display: flex; align-items: center; justify-content: space-between; padding: 12px 0; }
.head-btn { width: 32px; height: 32px; display: flex; align-items: center; justify-content: center; }
.head-title { font-size: 15px; font-weight: 700; }
.head-spacer { width: 32px; }
.tabs { display: flex; gap: 4px; margin-bottom: 12px; overflow-x: auto; }
.tab { flex: none; font-size: 12.5px; font-weight: 600; color: var(--ink-3); padding: 7px 13px; border-radius: 999px; background: var(--card); }
.tab.on { color: #fff; background: var(--brand); }
.order { background: var(--card); border-radius: var(--radius); padding: 13px 15px; margin-bottom: 10px; }
.order-head { display: flex; align-items: center; justify-content: space-between; padding-bottom: 10px; border-bottom: 1px solid var(--line); cursor: pointer; }
.order-no { font-size: 11.5px; color: var(--ink-3); }
.order-status { font-size: 12px; font-weight: 700; }
.st-init { color: var(--accent); }
.st-mid { color: var(--brand); }
.st-ok { color: var(--mint); }
.st-close { color: var(--ink-3); }
.order-item { display: flex; align-items: center; gap: 10px; padding: 10px 0; cursor: pointer; }
.item-ph { width: 46px; height: 46px; border-radius: 10px; background: var(--brand-soft); color: var(--brand); font-size: 18px; font-weight: 700; display: flex; align-items: center; justify-content: center; flex: none; }
.item-info { flex: 1; min-width: 0; }
.item-name { font-size: 13px; font-weight: 600; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.item-spec { font-size: 10.5px; color: var(--ink-3); margin-top: 2px; }
.item-right { text-align: right; }
.item-price { color: var(--accent); font-size: 12px; font-weight: 700; }
.item-count { font-size: 11px; color: var(--ink-3); margin-top: 2px; }
.order-foot { display: flex; align-items: center; justify-content: space-between; padding-top: 10px; border-top: 1px solid var(--line); }
.order-amount { font-size: 12px; color: var(--ink-2); }
.order-amount b { font-size: 15px; color: var(--ink); }
.order-ops { display: flex; gap: 8px; }
.op { font-size: 11.5px; font-weight: 600; border-radius: 999px; padding: 7px 15px; }
.op.line { color: var(--ink-2); border: 1px solid var(--line-2); }
.op.brand { color: #fff; background: var(--brand); }
</style>
