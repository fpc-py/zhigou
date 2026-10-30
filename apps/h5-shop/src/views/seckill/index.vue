<template>
  <div class="page">
    <!-- 顶栏 -->
    <header class="topbar">
      <button class="icon-btn" @click="$router.back()"><Icon name="back" /></button>
      <h1>限时秒杀</h1>
      <span class="topbar-tag">先到先得 · 售完即止</span>
    </header>

    <main class="body">
      <!-- 活动列表 -->
      <section v-if="loading">
        <div v-for="i in 3" :key="i" class="act-card">
          <div class="act-img skeleton" />
          <div class="act-main"><div class="skeleton line" style="width: 70%" /><div class="skeleton line" style="width: 45%" /></div>
        </div>
      </section>

      <EmptyState v-else-if="!activities.length" icon="flash" text="暂无进行中的秒杀活动" />

      <template v-else>
        <div v-for="a in activities" :key="a.id" class="act-card">
          <img v-if="a.imageUrl" :src="a.imageUrl" class="act-img" alt="" loading="lazy" />
          <div v-else class="act-img act-img-placeholder" />
          <div class="act-main">
            <h3 class="act-title">{{ a.title }}</h3>
            <div class="act-price">
              <span class="sec-price">秒杀价 ¥{{ (a.seckillPrice / 100).toFixed(2) }}</span>
              <span class="origin-price">原价 ¥{{ (a.originPrice / 100).toFixed(2) }}</span>
              <span class="save-tag">立省 ¥{{ ((a.originPrice - a.seckillPrice) / 100).toFixed(2) }}</span>
            </div>
            <div class="act-meta">每人限购 {{ a.perUserLimit }} 件 · 已抢 {{ a.sold }} / {{ a.total }} 件</div>
            <div class="stock-bar"><div class="stock-fill" :style="{ width: stockPct(a) + '%' }" /></div>

            <button class="buy-btn" :disabled="buying === a.id || soldOut(a)" @click="buy(a)">
              {{ soldOut(a) ? '已抢光' : buying === a.id ? '抢购中…' : '立即抢购' }}
            </button>
          </div>
        </div>
      </template>
    </main>

    <!-- 结果弹窗 -->
    <div v-if="modal" class="mask" @click.self="modal = null">
      <div class="sheet">
        <h3>{{ modal.title }}</h3>
        <p class="sheet-desc">{{ modal.desc }}</p>
        <div class="sheet-actions">
          <button class="btn ghost" @click="modal = null">{{ modal.okText ?? '知道了' }}</button>
          <button v-if="modal.toOrder" class="btn primary" @click="goOrder">去支付</button>
        </div>
      </div>
    </div>

    <div v-if="toast" class="toast">{{ toast }}</div>
  </div>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue';
import { useRouter } from 'vue-router';
import { getSeckillActivities, seckillBuy, releaseSeckill } from '@/api/marketing';
import { createOrder } from '@/api/order';
import type { SeckillActivity } from '@/api/marketing';
import Icon from '@/components/Icon.vue';
import EmptyState from '@/components/EmptyState.vue';

const router = useRouter();
const activities = ref<SeckillActivity[]>([]);
const loading = ref(true);
const buying = ref('');
const toast = ref('');
const modal = ref<{ title: string; desc: string; okText?: string; toOrder?: boolean } | null>(null);

function flash(msg: string) {
  toast.value = msg;
  setTimeout(() => (toast.value = ''), 2000);
}

function stockPct(a: SeckillActivity) {
  if (!a.total) return 0;
  return Math.min(100, Math.round((a.sold / a.total) * 100));
}

function soldOut(a: SeckillActivity) {
  return a.sold >= a.total;
}

async function load() {
  loading.value = true;
  try {
    activities.value = await getSeckillActivities();
  } catch {
    /* 已提示 */
  } finally {
    loading.value = false;
  }
}

async function buy(a: SeckillActivity) {
  buying.value = a.id;
  try {
    // 1) 营销侧预扣：防超卖 + 限购校验，返回秒杀价快照（无订单）
    const snap = await seckillBuy(a.id, 1);
    if (!snap) {
      flash('抢购失败，请重试');
      return;
    }
    // 2) 创建秒杀订单：订单侧以秒杀价计价（服务端向营销活动权威取价），不可叠加优惠券
    const requestId = `${Date.now()}-${Math.random().toString(36).slice(2, 8)}`;
    const order = await createOrder({
      requestId,
      skuItems: [{ skuId: String(snap.skuId), count: 1 }],
      seckillActivityId: a.id,
    });
    const orderId = order?.orderId;
    if (!orderId) throw new Error('秒杀订单创建失败');
    modal.value = {
      title: '抢购成功',
      desc: `已生成秒杀订单 ¥${(snap.seckillPrice / 100).toFixed(2)}，请在 15 分钟内完成支付`,
      okText: '稍后支付',
      toOrder: true,
    };
    (modal.value as any)._orderId = orderId;
  } catch (e) {
    // 预扣成功但建单失败（如活动窗口结束/服务异常）→ 释放预扣，归还库存
    try {
      await releaseSeckill(a.id, 1);
    } catch {
      /* 释放失败不阻塞提示 */
    }
    flash(e instanceof Error ? e.message : '抢购失败');
    await load();
  } finally {
    buying.value = '';
  }
}

function goOrder() {
  const orderId = (modal.value as any)?._orderId;
  modal.value = null;
  if (orderId) router.push(`/order/${orderId}`);
}

onMounted(load);
</script>

<style scoped>
.page { min-height: 100vh; background: #f6f7f9; padding-bottom: 32px; }
.topbar { display: flex; align-items: center; gap: 12px; padding: 14px 16px; background: #fff; position: sticky; top: 0; z-index: 10; }
.topbar h1 { font-size: 18px; margin: 0; flex: 1; }
.topbar-tag { font-size: 12px; color: #d92d20; background: #fef0ef; padding: 3px 10px; border-radius: 999px; }
.icon-btn { border: none; background: none; font-size: 20px; padding: 4px 2px; }
.body { padding: 12px 16px; display: flex; flex-direction: column; gap: 14px; }

.act-card { display: flex; gap: 12px; background: #fff; border-radius: 14px; padding: 14px; }
.act-img { width: 96px; height: 96px; border-radius: 10px; object-fit: cover; flex: none; background: #f0f2f5; }
.act-img-placeholder { background: linear-gradient(135deg, #fff0ec, #ffe8e5); }
.act-main { flex: 1; min-width: 0; }
.act-title { font-size: 14.5px; margin: 0 0 6px; }
.act-price { display: flex; align-items: center; gap: 8px; flex-wrap: wrap; }
.sec-price { color: #d92d20; font-size: 17px; font-weight: 800; }
.origin-price { color: #9aa0a6; text-decoration: line-through; font-size: 12px; }
.save-tag { color: #d92d20; background: #fff0ef; padding: 2px 8px; border-radius: 999px; font-size: 11.5px; font-weight: 700; }
.act-meta { font-size: 11.5px; color: #57606a; margin-top: 6px; }
.stock-bar { height: 6px; border-radius: 999px; background: #f0f2f5; margin-top: 8px; overflow: hidden; }
.stock-fill { height: 100%; border-radius: 999px; background: linear-gradient(90deg, #ff8a65, #d92d20); }
.buy-btn { width: 100%; margin-top: 10px; border: none; border-radius: 10px; background: #d92d20; color: #fff; font-size: 13.5px; font-weight: 700; padding: 10px 0; }
.buy-btn:disabled { opacity: 0.55; }

.mask { position: fixed; inset: 0; background: rgba(0,0,0,.45); display: flex; align-items: flex-end; justify-content: center; z-index: 50; }
.sheet { background: #fff; border-radius: 18px 18px 0 0; padding: 22px 20px 26px; width: 100%; max-width: 460px; }
.sheet h3 { font-size: 16.5px; margin-bottom: 8px; }
.sheet-desc { font-size: 13px; color: #57606a; line-height: 1.6; }
.sheet-actions { display: flex; gap: 10px; margin-top: 18px; }
.btn { flex: 1; border: none; border-radius: 12px; padding: 12px 0; font-size: 14px; font-weight: 700; cursor: pointer; }
.btn.ghost { background: #f0f2f5; color: #333; }
.btn.primary { background: #d92d20; color: #fff; }
.btn:disabled { opacity: .5; cursor: default; }
.toast { position: fixed; left: 50%; bottom: 48px; transform: translateX(-50%); background: rgba(0,0,0,.78); color: #fff; padding: 10px 18px; border-radius: 999px; font-size: 13px; z-index: 99; white-space: nowrap; }
.skeleton { background: linear-gradient(90deg, #f0f2f5 25%, #e5e8ec 37%, #f0f2f5 63%); background-size: 400% 100%; animation: shimmer 1.4s infinite; border-radius: 10px; }
.skeleton.line { height: 14px; margin: 6px 0; }
@keyframes shimmer { 0% { background-position: 100% 0; } 100% { background-position: 0 0; } }
</style>
