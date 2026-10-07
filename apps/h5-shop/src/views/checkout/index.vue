<template>
  <div class="page checkout-page">
    <header class="page-head">
      <button class="head-btn" @click="back"><Icon name="back" /></button>
      <span class="head-title">确认订单</span>
      <span class="head-spacer" />
    </header>

    <Skeleton v-if="loading" w="100%" h="90px" :repeat="4" />
    <EmptyState v-else-if="items.length === 0" illustration="📦" text="没有可结算的商品" />

    <template v-else>
      <!-- 收货地址 -->
      <section class="addr card" @click="chooseAddress">
        <template v-if="address">
          <div class="addr-icon"><Icon name="loc" /></div>
          <div class="addr-info">
            <p class="addr-row"><b>{{ address.receiverName }}</b><span>{{ address.receiverPhone }}</span><i v-if="address.isDefault === 1">默认</i></p>
            <p class="addr-detail">{{ address.province }}{{ address.city }}{{ address.district }} {{ address.detail }}</p>
          </div>
          <Icon name="chev" size="xs" />
        </template>
        <template v-else>
          <div class="addr-icon"><Icon name="loc" /></div>
          <p class="addr-empty">请选择收货地址</p>
          <Icon name="chev" size="xs" />
        </template>
      </section>

      <!-- 商品清单 -->
      <section class="goods card">
        <h3 class="sec-title">商品清单</h3>
        <div v-for="g in items" :key="g.skuId" class="good-row">
          <img v-if="g.image" :src="g.image" :alt="g.name" class="good-img" />
          <div v-else class="good-img ph">🛍️</div>
          <div class="good-info">
            <p class="good-name">{{ g.name || '商品' }}</p>
            <p v-if="g.specValue" class="good-spec">{{ g.specName || '规格' }}: {{ g.specValue }}</p>
          </div>
          <div class="good-right">
            <p class="good-price"><b>¥{{ formatPrice(g.price) }}</b></p>
            <p class="good-count">x{{ g.count }}</p>
          </div>
        </div>
      </section>

      <!-- 优惠券 -->
      <section class="coupon card" @click="chooseCoupon">
        <span class="coupon-label">优惠券</span>
        <span class="coupon-value" :class="{ active: coupon }">{{ coupon ? `已选 1 张，省 ¥${formatPrice(couponSave)}` : discountTip }}</span>
        <Icon name="chev" size="xs" />
      </section>

      <!-- 金额明细 -->
      <section class="amount card">
        <div class="amount-row"><span>商品金额</span><b>¥{{ formatPrice(goodsTotal) }}</b></div>
        <div class="amount-row save"><span>优惠</span><b>− ¥{{ formatPrice(totalDiscount) }}</b></div>
        <div class="amount-row"><span>运费</span><b>{{ freight.freeShipping ? '免运费' : `¥${formatPrice(freight.freightFee)}` }}</b></div>
        <div class="amount-row total"><span>应付</span><b class="pay">¥{{ formatPrice(payAmount) }}</b></div>
      </section>

      <!-- 提交 -->
      <div class="submit-bar">
        <div class="submit-left">
          <p class="submit-label">应付</p>
          <p class="submit-amount"><b>¥{{ formatPrice(payAmount) }}</b></p>
        </div>
        <button class="submit-btn" :disabled="submitting" @click="submit">
          {{ submitting ? '提交中…' : '提交订单' }}
        </button>
      </div>
    </template>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { getCartMine } from '@/api/cart';
import { getProductPage } from '@/api/product';
import type { ProductPageItem } from '@/api/product';
import { listAddresses } from '@/api/user';
import type { AddressItem } from '@/api/user';
import { getMyCoupons, calculateDiscount } from '@/api/marketing';
import type { UserCoupon } from '@/api/marketing';
import { calculateFreight } from '@/api/logistics';
import { createOrder } from '@/api/order';
import { createPayment, mockPay } from '@/api/payment';
import { showToast, formatPrice } from '@/utils';
import Icon from '@/components/Icon.vue';
import Skeleton from '@/components/Skeleton.vue';
import EmptyState from '@/components/EmptyState.vue';

interface CheckoutItem {
  skuId: string;
  count: number;
  name?: string;
  specName?: string;
  specValue?: string;
  price?: number;
  image?: string;
}

const route = useRoute();
const router = useRouter();
const loading = ref(true);
const submitting = ref(false);
const items = ref<CheckoutItem[]>([]);
const address = ref<AddressItem | null>(null);
const addresses = ref<AddressItem[]>([]);
const coupons = ref<UserCoupon[]>([]);
const coupon = ref<UserCoupon | null>(null);
const couponSave = ref(0);
const totalDiscount = ref(0);
const freight = ref({ freightFee: 0, freeShipping: true, reason: '' });

const goodsTotal = computed(() => items.value.reduce((s, i) => s + (i.price ?? 0) * i.count, 0));
const payAmount = computed(() => Math.max(0, goodsTotal.value - totalDiscount.value + (freight.value.freeShipping ? 0 : freight.value.freightFee)));

const discountTip = computed(() => {
  if (totalDiscount.value > 0) return `已自动优惠 ¥${formatPrice(totalDiscount.value)}`;
  return '暂无可用';
});

function back() {
  if (route.query.from === 'cart') router.back();
  else router.push('/');
}

/** 加载 skuId → 商品信息映射 */
async function loadSkuMap(): Promise<Map<string, ProductPageItem>> {
  const map = new Map<string, ProductPageItem>();
  try {
    const res = await getProductPage({ pageSize: 100 });
    for (const spu of res.records) {
      for (const sku of spu.skus ?? []) {
        map.set(sku.skuId, spu);
      }
    }
  } catch {
    /* 忽略 */
  }
  return map;
}

function enrich(itemsToEnrich: Array<{ skuId: string; count: number }>, map: Map<string, ProductPageItem>) {
  return itemsToEnrich.map((it) => {
    const spu = map.get(it.skuId);
    const sku = spu?.skus?.find((s) => s.skuId === it.skuId);
    return {
      ...it,
      name: spu?.name,
      specName: sku?.specName,
      specValue: sku?.specValue,
      price: sku?.price ?? spu?.priceMin,
      image: sku?.image ?? spu?.mainImage,
    };
  });
}

async function loadItems() {
  const from = route.query.from;
  if (from === 'cart') {
    const raw = sessionStorage.getItem('checkoutItems');
    const picked: Array<{ skuId: string; count: number }> = raw ? JSON.parse(raw) : [];
    const map = await loadSkuMap();
    items.value = enrich(picked, map);
  } else {
    const skuId = route.query.skuId as string;
    const count = Number(route.query.count ?? 1);
    if (skuId) {
      const map = await loadSkuMap();
      items.value = enrich([{ skuId, count }], map);
    }
  }
}

async function loadAddresses() {
  try {
    addresses.value = await listAddresses();
    const fromAddr = route.query.addressId as string | undefined;
    address.value = fromAddr
      ? addresses.value.find((a) => a.addressId === fromAddr) ?? null
      : addresses.value.find((a) => a.isDefault === 1) ?? addresses.value[0] ?? null;
  } catch {
    /* 忽略 */
  }
}

async function loadCoupons() {
  try {
    coupons.value = await getMyCoupons('UNUSED');
  } catch {
    /* 忽略 */
  }
}

async function recalc() {
  if (items.value.length === 0) return;
  const body = {
    items: items.value.map((i) => ({ skuId: i.skuId, count: i.count, price: i.price ?? 0 })),
    couponId: coupon.value ? Number(coupon.value.id) : null,
  };
  const res = await calculateDiscount(body).catch(() => null);
  if (res) {
    totalDiscount.value = res.discountAmount ?? 0;
    const chosen = res.availableCoupons?.find((c) => c.couponId === String(coupon.value?.id));
    couponSave.value = chosen?.saveAmount ?? 0;
  }
  const f = await calculateFreight(goodsTotal.value - totalDiscount.value).catch(() => null);
  if (f) freight.value = f;
}

function chooseAddress() {
  router.push({ path: '/address', query: { from: 'checkout' } });
}

async function chooseCoupon() {
  if (coupons.value.length === 0) {
    showToast('暂无可用优惠券');
    return;
  }
  // 简单轮换选择下一张（演示环境）
  const idx = coupons.value.findIndex((c) => c.id === coupon.value?.id);
  coupon.value = coupons.value[(idx + 1) % coupons.value.length] ?? null;
  await recalc();
}

async function submit() {
  if (!address.value) {
    showToast('请先选择收货地址');
    return;
  }
  if (submitting.value) return;
  submitting.value = true;
  try {
    const requestId = `${Date.now()}-${Math.random().toString(36).slice(2, 8)}`;
    const order = await createOrder({
      requestId,
      skuItems: items.value.map((i) => ({ skuId: i.skuId, count: i.count })),
      couponId: coupon.value ? Number(coupon.value.id) : null,
    });
    if (!order) {
      showToast('下单失败，请重试');
      return;
    }
    // 沙箱支付
    const pay = await createPayment(String(order.orderId), order.payAmount ?? payAmount.value);
    if (pay?.paymentNo) {
      await mockPay(pay.paymentNo);
    }
    showToast('支付成功');
    router.replace(`/order/${order.orderId}`);
  } catch {
    /* 已提示 */
  } finally {
    submitting.value = false;
  }
}

onMounted(async () => {
  await Promise.all([loadItems(), loadAddresses(), loadCoupons()]);
  await recalc();
  loading.value = false;
});
</script>

<style scoped>
.checkout-page { min-height: 100vh; padding: 0 14px 110px; }
.page-head { display: flex; align-items: center; justify-content: space-between; padding: 12px 0; }
.head-btn { width: 32px; height: 32px; display: flex; align-items: center; justify-content: center; }
.head-title { font-size: 15px; font-weight: 700; }
.head-spacer { width: 32px; }
.card { background: var(--card); border-radius: var(--radius); padding: 14px 16px; margin-bottom: 10px; }
.addr { display: flex; align-items: center; gap: 10px; cursor: pointer; }
.addr-icon { width: 36px; height: 36px; border-radius: 12px; background: var(--brand-soft); color: var(--brand); display: flex; align-items: center; justify-content: center; flex: none; }
.addr-info { flex: 1; min-width: 0; }
.addr-row { display: flex; align-items: center; gap: 8px; }
.addr-row b { font-size: 14px; }
.addr-row span { font-size: 12.5px; color: var(--ink-2); }
.addr-row i { font-style: normal; font-size: 9.5px; color: var(--brand); background: var(--brand-soft); border-radius: 999px; padding: 2px 7px; }
.addr-detail { font-size: 12px; color: var(--ink-2); margin-top: 3px; }
.addr-empty { flex: 1; font-size: 13px; color: var(--ink-3); }
.sec-title { font-size: 14px; font-weight: 700; margin-bottom: 10px; }
.good-row { display: flex; align-items: center; gap: 10px; padding: 8px 0; }
.good-img { width: 54px; height: 54px; border-radius: 10px; object-fit: cover; background: linear-gradient(135deg, #f6f7fb, #eef0f7); flex: none; }
.good-img.ph { display: flex; align-items: center; justify-content: center; font-size: 22px; }
.good-info { flex: 1; min-width: 0; }
.good-name { font-size: 13px; font-weight: 600; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.good-spec { font-size: 11px; color: var(--ink-3); margin-top: 2px; }
.good-right { text-align: right; }
.good-price { color: var(--accent); font-size: 11px; font-weight: 600; }
.good-price b { font-size: 14px; }
.good-count { font-size: 11px; color: var(--ink-3); margin-top: 2px; }
.coupon { display: flex; align-items: center; gap: 10px; cursor: pointer; }
.coupon-label { font-size: 13px; font-weight: 600; }
.coupon-value { flex: 1; text-align: right; font-size: 12px; color: var(--ink-3); }
.coupon-value.active { color: var(--accent); font-weight: 700; }
.amount-row { display: flex; justify-content: space-between; font-size: 12.5px; color: var(--ink-2); padding: 5px 0; }
.amount-row b { color: var(--ink); }
.amount-row.save b { color: var(--accent); }
.amount-row.total { border-top: 1px dashed var(--line-2); margin-top: 6px; padding-top: 10px; font-weight: 700; }
.amount-row.total .pay { font-size: 19px; color: var(--accent); }
.submit-bar {
  position: fixed;
  bottom: 0;
  left: 50%;
  transform: translateX(-50%);
  width: 100%;
  max-width: 414px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 10px 14px calc(10px + env(safe-area-inset-bottom, 0px));
  background: rgba(255,255,255,.97);
  border-top: 1px solid var(--line);
}
.submit-left { text-align: right; }
.submit-label { font-size: 10px; color: var(--ink-3); }
.submit-amount b { font-size: 20px; color: var(--accent); }
.submit-btn { height: 42px; padding: 0 26px; border-radius: 999px; background: var(--brand); color: #fff; font-size: 14px; font-weight: 600; }
.submit-btn:disabled { opacity: 0.4; }
</style>
