<template>
  <div class="page order-detail-page">
    <header class="page-head">
      <button class="head-btn" @click="router.back()"><Icon name="back" /></button>
      <span class="head-title">订单详情</span>
      <span class="head-spacer" />
    </header>

    <Skeleton v-if="loading" w="100%" h="120px" :repeat="3" />
    <EmptyState v-else-if="!order" illustration="📦" text="订单不存在" />

    <template v-else>
      <!-- 状态卡 -->
      <section class="status-hero" :class="statusClass(order.orderStatus)">
        <p class="status-title">{{ statusText(order.orderStatus) }}</p>
        <p class="status-sub">{{ statusSub(order.orderStatus) }}</p>
      </section>

      <!-- 订单信息 -->
      <section class="card info-card">
        <h3 class="sec-title">订单信息</h3>
        <div class="info-row"><span>订单编号</span><b>{{ order.orderId }}</b></div>
        <div class="info-row"><span>下单时间</span><b>-</b></div>
        <div class="info-row"><span>订单金额</span><b>¥{{ formatPrice(order.totalAmount) }}</b></div>
        <div class="info-row"><span>实付金额</span><b class="pay">¥{{ formatPrice(order.payAmount) }}</b></div>
      </section>

      <!-- 商品 -->
      <section class="card">
        <h3 class="sec-title">商品清单</h3>
        <div v-for="it in order.items" :key="it.skuId" class="good-row">
          <div class="good-ph">{{ it.skuName?.slice(0, 1) || '货' }}</div>
          <div class="good-info">
            <p class="good-name">{{ it.skuName || '商品' }}</p>
            <p class="good-spec">SKU {{ it.skuId }}</p>
          </div>
          <div class="good-right">
            <p class="good-price">¥{{ formatPrice(it.price) }}</p>
            <p class="good-count">x{{ it.count }}</p>
          </div>
        </div>
      </section>

      <!-- 操作 -->
      <div class="order-ops">
        <button v-if="order.orderStatus === 'INIT'" class="op line" @click="cancel">取消订单</button>
        <button v-if="order.orderStatus === 'INIT'" class="op brand" @click="pay">立即支付</button>
        <button v-else class="op line" @click="toast('售后申请功能规划中')">申请售后</button>
      </div>
    </template>
  </div>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { getOrderDetail, cancelOrder } from '@/api/order';
import type { OrderDetail } from '@/api/order';
import { createPayment, mockPay } from '@/api/payment';
import { showToast, formatPrice } from '@/utils';
import Icon from '@/components/Icon.vue';
import Skeleton from '@/components/Skeleton.vue';
import EmptyState from '@/components/EmptyState.vue';

const route = useRoute();
const router = useRouter();
const orderId = route.params.orderId as string;
const loading = ref(true);
const order = ref<OrderDetail | null>(null);

function statusText(s: string) {
  const map: Record<string, string> = {
    INIT: '等待付款', PAID: '商家备货中', SHIPPED: '配送中', COMPLETED: '交易完成',
    CLOSED: '订单已关闭', REFUNDING: '退款处理中', REFUNDED: '已退款',
  };
  return map[s] ?? s;
}

function statusSub(s: string) {
  const map: Record<string, string> = {
    INIT: '请尽快完成支付，超时订单将自动关闭',
    PAID: '我们会尽快为你发货',
    SHIPPED: '包裹正在路上，请注意查收',
    COMPLETED: '感谢你在智购购物，欢迎再次光临',
    CLOSED: '如有疑问请联系客服',
  };
  return map[s] ?? '';
}

function statusClass(s: string) {
  if (s === 'INIT') return 'hero-init';
  if (s === 'PAID' || s === 'SHIPPED') return 'hero-mid';
  if (s === 'COMPLETED') return 'hero-ok';
  return 'hero-close';
}

async function load() {
  loading.value = true;
  try {
    order.value = await getOrderDetail(orderId);
  } catch {
    /* 已提示 */
  } finally {
    loading.value = false;
  }
}

async function cancel() {
  try {
    await cancelOrder(orderId);
    showToast('订单已取消');
    load();
  } catch {
    /* 已提示 */
  }
}

async function pay() {
  if (!order.value) return;
  try {
    const pay = await createPayment(orderId, order.value.payAmount ?? order.value.totalAmount);
    if (pay?.paymentNo) {
      await mockPay(pay.paymentNo);
      showToast('支付成功');
      load();
    } else {
      showToast('支付创建失败');
    }
  } catch {
    /* 已提示 */
  }
}

function toast(msg: string) {
  showToast(msg);
}

onMounted(load);
</script>

<style scoped>
.order-detail-page { min-height: 100vh; padding: 0 14px 120px; }
.page-head { display: flex; align-items: center; justify-content: space-between; padding: 12px 0; }
.head-btn { width: 32px; height: 32px; display: flex; align-items: center; justify-content: center; }
.head-title { font-size: 15px; font-weight: 700; }
.head-spacer { width: 32px; }
.status-hero { border-radius: var(--radius); padding: 20px 18px; color: #fff; margin-bottom: 12px; }
.hero-init { background: linear-gradient(120deg, #FF5C39, #FF8A5C); }
.hero-mid { background: linear-gradient(120deg, #4C5CFF, #7A6BFF); }
.hero-ok { background: linear-gradient(120deg, #00A87E, #2FC7A0); }
.hero-close { background: linear-gradient(120deg, #8B90A0, #B6BAC7); }
.status-title { font-size: 20px; font-weight: 800; }
.status-sub { font-size: 12px; opacity: 0.9; margin-top: 5px; }
.card { background: var(--card); border-radius: var(--radius); padding: 14px 16px; margin-bottom: 12px; }
.sec-title { font-size: 14px; font-weight: 700; margin-bottom: 10px; }
.info-row { display: flex; justify-content: space-between; font-size: 12.5px; color: var(--ink-2); padding: 5px 0; }
.info-row b { color: var(--ink); font-weight: 600; }
.info-row .pay { color: var(--accent); font-weight: 700; }
.good-row { display: flex; align-items: center; gap: 10px; padding: 8px 0; }
.good-ph { width: 46px; height: 46px; border-radius: 10px; background: var(--brand-soft); color: var(--brand); font-size: 18px; font-weight: 700; display: flex; align-items: center; justify-content: center; flex: none; }
.good-info { flex: 1; min-width: 0; }
.good-name { font-size: 13px; font-weight: 600; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.good-spec { font-size: 10.5px; color: var(--ink-3); margin-top: 2px; }
.good-right { text-align: right; }
.good-price { color: var(--accent); font-size: 12px; font-weight: 700; }
.good-count { font-size: 11px; color: var(--ink-3); }
.order-ops { position: fixed; bottom: 0; left: 50%; transform: translateX(-50%); width: 100%; max-width: 414px; display: flex; gap: 10px; padding: 12px 16px calc(12px + env(safe-area-inset-bottom, 0px)); background: rgba(255,255,255,.97); border-top: 1px solid var(--line); }
.op { flex: 1; height: 42px; border-radius: 999px; font-size: 13.5px; font-weight: 600; }
.op.line { color: var(--ink-2); border: 1px solid var(--line-2); }
.op.brand { background: var(--brand); color: #fff; }
</style>
