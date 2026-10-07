<template>
  <div class="page cart-page">
    <header class="page-head">
      <button class="head-btn" @click="router.back()"><Icon name="back" /></button>
      <span class="head-title">购物车</span>
      <button class="head-clear" @click="clearAll">清空</button>
    </header>

    <Skeleton v-if="loading" w="100%" h="90px" :repeat="3" />
    <EmptyState v-else-if="visible.length === 0" illustration="🛒" text="购物车还是空的" />

    <div v-else class="cart-list">
      <div v-for="item in visible" :key="item.skuId" class="cart-item card">
        <button class="check" :class="{ on: item.selected }" @click="toggleSelect(item)">
          <Icon v-if="item.selected" name="check" size="xs" />
        </button>
        <img v-if="item.image" :src="item.image" :alt="item.name" class="item-img" />
        <div v-else class="item-img ph">🛍️</div>
        <div class="item-info">
          <p class="item-name">{{ item.name || '商品' }}</p>
          <p v-if="item.specValue" class="item-spec">{{ item.specName || '规格' }}: {{ item.specValue }}</p>
          <div class="item-foot">
            <p class="item-price"><b>¥{{ formatPrice(item.price) }}</b></p>
            <div class="stepper">
              <button class="step" @click="changeCount(item, -1)">−</button>
              <span class="count">{{ item.count }}</span>
              <button class="step" @click="changeCount(item, 1)">+</button>
            </div>
          </div>
        </div>
      </div>
    </div>

    <!-- 结算栏 -->
    <div v-if="visible.length" class="settle-bar">
      <button class="select-all" @click="toggleAll">
        <span class="check" :class="{ on: allSelected }"><Icon v-if="allSelected" name="check" size="xs" /></span>
        全选
      </button>
      <div class="settle-total">
        <p class="total-label">合计：<b>¥{{ formatPrice(totalPrice) }}</b></p>
        <p class="total-count">已选 {{ selectedCount }} 件</p>
      </div>
      <button class="settle-btn" :disabled="selectedCount === 0" @click="goCheckout">
        去结算 ({{ selectedCount }})
      </button>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue';
import { useRouter } from 'vue-router';
import { getCartMine, updateCart, removeCart } from '@/api/cart';
import type { CartItem } from '@/api/cart';
import { showToast, formatPrice } from '@/utils';
import Icon from '@/components/Icon.vue';
import Skeleton from '@/components/Skeleton.vue';
import EmptyState from '@/components/EmptyState.vue';

const router = useRouter();
const loading = ref(true);
const items = ref<CartItem[]>([]);

const visible = computed(() => items.value.filter((i) => i.count > 0));
const selected = computed(() => visible.value.filter((i) => i.selected));
const selectedCount = computed(() => selected.value.reduce((s, i) => s + i.count, 0));
const totalPrice = computed(() => selected.value.reduce((s, i) => s + (i.price ?? 0) * i.count, 0));
const allSelected = computed(() => visible.value.length > 0 && visible.value.every((i) => i.selected));

async function load() {
  loading.value = true;
  try {
    items.value = await getCartMine();
  } catch {
    /* 已提示 */
  } finally {
    loading.value = false;
  }
}

async function toggleSelect(item: CartItem) {
  const next = !item.selected;
  item.selected = next;
  await updateCart(item.skuId, { selected: next }).catch(() => {});
}

async function toggleAll() {
  const next = !allSelected.value;
  for (const i of visible.value) {
    i.selected = next;
    await updateCart(i.skuId, { selected: next }).catch(() => {});
  }
}

async function changeCount(item: CartItem, delta: number) {
  const next = Math.max(0, item.count + delta);
  if (next === 0) {
    try {
      await removeCart(item.skuId);
      item.count = 0;
    } catch {
      /* 已提示 */
    }
    return;
  }
  item.count = next;
  await updateCart(item.skuId, { count: next }).catch(() => {});
}

async function clearAll() {
  if (visible.value.length === 0) return;
  try {
    // 后端 clear 语义为"清空选中"，这里逐条删除更直观
    for (const i of [...visible.value]) {
      await removeCart(i.skuId);
      i.count = 0;
    }
    showToast('购物车已清空');
  } catch {
    /* 已提示 */
  }
}

function goCheckout() {
  const payload = selected.value.map((i) => ({ skuId: i.skuId, count: i.count }));
  sessionStorage.setItem('checkoutItems', JSON.stringify(payload));
  router.push('/checkout?from=cart');
}

onMounted(load);
</script>

<style scoped>
.cart-page { min-height: 100vh; padding: 0 14px 100px; }
.page-head { display: flex; align-items: center; justify-content: space-between; padding: 12px 0; }
.head-btn { width: 32px; height: 32px; display: flex; align-items: center; justify-content: center; }
.head-title { font-size: 15px; font-weight: 700; }
.head-clear { font-size: 12.5px; color: var(--ink-3); }
.cart-list { display: flex; flex-direction: column; gap: 10px; }
.card { background: var(--card); border-radius: var(--radius); padding: 12px; }
.cart-item { display: flex; align-items: center; gap: 10px; }
.check {
  width: 20px;
  height: 20px;
  border-radius: 50%;
  border: 1.5px solid var(--line-2);
  display: flex;
  align-items: center;
  justify-content: center;
  flex: none;
  color: #fff;
}
.check.on { background: var(--brand); border-color: var(--brand); }
.item-img { width: 66px; height: 66px; border-radius: 12px; object-fit: cover; background: linear-gradient(135deg, #f6f7fb, #eef0f7); flex: none; }
.item-img.ph { display: flex; align-items: center; justify-content: center; font-size: 26px; }
.item-info { flex: 1; min-width: 0; }
.item-name { font-size: 13.5px; font-weight: 600; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.item-spec { font-size: 11px; color: var(--ink-3); margin-top: 3px; }
.item-foot { display: flex; align-items: center; justify-content: space-between; margin-top: 8px; }
.item-price { color: var(--accent); font-size: 11.5px; font-weight: 600; }
.item-price b { font-size: 15px; }
.stepper { display: flex; align-items: center; gap: 8px; }
.step { width: 22px; height: 22px; border-radius: 7px; background: var(--bg); color: var(--ink-2); font-size: 15px; display: flex; align-items: center; justify-content: center; }
.count { font-size: 13px; font-weight: 600; min-width: 16px; text-align: center; }
.settle-bar {
  position: fixed;
  bottom: 0;
  left: 50%;
  transform: translateX(-50%);
  width: 100%;
  max-width: 414px;
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 10px 14px calc(10px + env(safe-area-inset-bottom, 0px));
  background: rgba(255,255,255,.97);
  border-top: 1px solid var(--line);
}
.select-all { display: flex; align-items: center; gap: 6px; font-size: 12px; color: var(--ink-2); }
.settle-total { flex: 1; text-align: right; }
.total-label { font-size: 11px; color: var(--ink-2); }
.total-label b { font-size: 17px; color: var(--accent); }
.total-count { font-size: 10px; color: var(--ink-3); }
.settle-btn { height: 40px; padding: 0 22px; border-radius: 999px; background: var(--brand); color: #fff; font-size: 13.5px; font-weight: 600; }
.settle-btn:disabled { opacity: 0.4; }
</style>
