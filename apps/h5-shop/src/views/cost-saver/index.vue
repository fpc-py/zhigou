<template>
  <div class="page">
    <!-- 顶栏 -->
    <header class="topbar">
      <button class="icon-btn" @click="$router.back()"><Icon name="back" /></button>
      <h1>省钱助手</h1>
      <span class="topbar-tag" v-if="overview">{{ overview.watchCount }} 件关注中</span>
    </header>

    <main class="body">
      <!-- 第一屏：总节省（alpha moment） -->
      <section v-if="overview" class="hero">
        <div class="hero-label">已帮你省</div>
        <div class="hero-amount">¥{{ (overview.totalSaving / 100).toFixed(2) }}</div>
        <div class="hero-summary">{{ overview.summary }}</div>
        <div class="hero-legend">
          <span v-if="overview.cartDiscountSaving > 0">满减 ¥{{ (overview.cartDiscountSaving / 100).toFixed(2) }}</span>
          <span v-if="overview.compareSaving > 0">比价 ¥{{ (overview.compareSaving / 100).toFixed(2) }}</span>
          <span v-if="overview.noticeSaving > 0">降价 ¥{{ (overview.noticeSaving / 100).toFixed(2) }}</span>
          <span v-if="overview.couponBestSaving > 0">可用券 ¥{{ (overview.couponBestSaving / 100).toFixed(2) }}</span>
          <span v-if="!overview.totalSaving && !overview.couponBestSaving" class="muted">把商品加入购物车，我会帮你算出最优买法</span>
        </div>
      </section>
      <div v-else class="hero skeleton" style="height: 130px" />

      <!-- 降价提醒 -->
      <section class="sec">
        <h2 class="sec-title">
          降价提醒
          <span v-if="unreadCount" class="badge">{{ unreadCount }}</span>
          <span class="sec-hint">关注制 · 默认零打扰</span>
        </h2>
        <EmptyState v-if="!notices.length" icon="tag" text="暂无降价提醒，去关注一个心仪商品吧" small />
        <div v-for="n in notices" :key="n.id" class="notice-row" :class="{ unread: !n.readFlag }" @click="read(n)">
          <div class="notice-main">
            <div class="notice-title">{{ n.skuName }}</div>
            <div class="notice-price">
              <span class="old">¥{{ (n.oldPrice / 100).toFixed(2) }}</span>
              <span class="arrow">→</span>
              <span class="new">¥{{ (n.newPrice / 100).toFixed(2) }}</span>
              <span class="save-tag">降了 ¥{{ (n.dropAmount / 100).toFixed(2) }}</span>
            </div>
          </div>
          <span v-if="!n.readFlag" class="dot" />
        </div>
      </section>

      <!-- 添加关注 -->
      <section class="sec">
        <h2 class="sec-title">关注降价 <span class="sec-hint">目标价可选</span></h2>
        <div class="watch-form">
          <input v-model.trim="skuInput" class="input" placeholder="输入商品 SKU ID" />
          <input v-model.trim="targetInput" class="input small" placeholder="目标价(元，可不填)" />
          <button class="btn primary" :disabled="busy || !skuInput" @click="addWatch">
            {{ busy ? '关注中…' : '关注' }}
          </button>
        </div>
        <div v-if="watchError" class="err">{{ watchError }}</div>
      </section>

      <!-- 我的关注 -->
      <section class="sec">
        <h2 class="sec-title">我的关注</h2>
        <EmptyState v-if="!alerts.length" icon="eye" text="还没有关注任何商品" small />
        <div v-for="a in alerts" :key="a.id" class="alert-row">
          <div class="alert-main">
            <div class="notice-title">{{ a.skuName }} <span class="sku-id">#{{ a.skuId }}</span></div>
            <div class="notice-price">
              <span class="new">¥{{ (a.currentPrice / 100).toFixed(2) }}</span>
              <span class="muted">关注时 ¥{{ (a.watchPrice / 100).toFixed(2) }}</span>
              <span v-if="a.dropped" class="save-tag">已降 ¥{{ (a.dropAmount / 100).toFixed(2) }}</span>
              <span v-else class="flat-tag">未降价</span>
            </div>
          </div>
          <button class="mini-btn" @click="openHistory(a.skuId)">趋势</button>
          <button class="mini-btn danger" @click="removeWatch(a.skuId)">取消</button>
        </div>
      </section>

      <!-- 历史价格 -->
      <section v-if="history" class="sec">
        <h2 class="sec-title">历史价格 · {{ history.skuName }}</h2>
        <div class="history-card">
          <div class="history-main">
            <span class="new">现价 ¥{{ (history.currentPrice / 100).toFixed(2) }}</span>
            <span class="muted">近 {{ history.sampledDays }} 天最低 ¥{{ (history.lowestPrice / 100).toFixed(2) }}</span>
            <span v-if="history.diffFromLowest > 0" class="save-tag">比最低贵 ¥{{ (history.diffFromLowest / 100).toFixed(2) }}</span>
            <span v-else class="flat-tag">当前即最低</span>
          </div>
          <div class="history-summary">{{ history.summary }}</div>
        </div>
      </section>
    </main>

    <div v-if="toast" class="toast">{{ toast }}</div>
  </div>
</template>

<script setup lang="ts">
import { onMounted, ref, computed } from 'vue';
import Icon from '@/components/Icon.vue';
import EmptyState from '@/components/EmptyState.vue';
import {
  fetchOverview, fetchAlerts, fetchNotices,
  watchSku, unwatchSku, fetchHistory, markNoticeRead,
  type CostSaverOverview, type PriceAlertItem, type PriceNotice, type PriceHistory,
} from '@/api/cost-saver';

const overview = ref<CostSaverOverview | null>(null);
const alerts = ref<PriceAlertItem[]>([]);
const notices = ref<PriceNotice[]>([]);
const history = ref<PriceHistory | null>(null);
const skuInput = ref('');
const targetInput = ref('');
const busy = ref(false);
const watchError = ref('');
const toast = ref('');
const unreadCount = computed(() => overview.value?.unreadNotices ?? notices.value.filter((n) => !n.readFlag).length);

function flash(msg: string) {
  toast.value = msg;
  setTimeout(() => (toast.value = ''), 1800);
}

async function loadAll() {
  overview.value = await fetchOverview();
  notices.value = await fetchNotices(true);
  alerts.value = await fetchAlerts();
}

async function addWatch() {
  busy.value = true;
  watchError.value = '';
  try {
    const target = targetInput.value ? Math.round(parseFloat(targetInput.value) * 100) : null;
    if (target !== null && Number.isNaN(target)) throw new Error('目标价格式不正确');
    await watchSku(skuInput.value, target);
    skuInput.value = '';
    targetInput.value = '';
    flash('已关注，降价会第一时间提醒你');
    await loadAll();
  } catch (e) {
    watchError.value = e instanceof Error ? e.message : '关注失败，请检查 SKU 是否存在';
  } finally {
    busy.value = false;
  }
}

async function removeWatch(skuId: string) {
  await unwatchSku(skuId);
  flash('已取消关注');
  await loadAll();
}

async function openHistory(skuId: string) {
  history.value = await fetchHistory(skuId, 30);
}

async function read(n: PriceNotice) {
  if (!n.readFlag) {
    await markNoticeRead(n.id);
    n.readFlag = 1;
    if (overview.value) overview.value.unreadNotices = Math.max(0, overview.value.unreadNotices - 1);
  }
}

onMounted(loadAll);
</script>

<style scoped>
.page { min-height: 100vh; background: #f6f7f9; padding-bottom: 32px; }
.topbar { display: flex; align-items: center; gap: 12px; padding: 14px 16px; background: #fff; position: sticky; top: 0; z-index: 10; }
.topbar h1 { font-size: 18px; margin: 0; flex: 1; }
.topbar-tag { font-size: 12px; color: #0969da; background: #e8f1ff; padding: 3px 10px; border-radius: 999px; }
.icon-btn { border: none; background: none; font-size: 20px; padding: 4px 2px; }
.body { padding: 12px 16px; display: flex; flex-direction: column; gap: 16px; }

.hero { background: linear-gradient(135deg, #ff6b35, #ff2d55); color: #fff; border-radius: 16px; padding: 20px; }
.hero-label { font-size: 13px; opacity: .85; }
.hero-amount { font-size: 40px; font-weight: 800; margin: 2px 0 6px; }
.hero-summary { font-size: 13px; line-height: 1.6; opacity: .95; }
.hero-legend { display: flex; flex-wrap: wrap; gap: 8px; margin-top: 12px; }
.hero-legend span { background: rgba(255,255,255,.2); padding: 3px 10px; border-radius: 999px; font-size: 12px; }
.hero-legend .muted { background: none; opacity: .8; }

.sec { background: #fff; border-radius: 14px; padding: 14px; }
.sec-title { font-size: 15px; margin: 0 0 10px; display: flex; align-items: center; gap: 6px; }
.sec-hint { font-size: 11px; color: #9aa0a6; font-weight: 400; }
.badge { background: #ff2d55; color: #fff; font-size: 11px; padding: 1px 7px; border-radius: 999px; }

.notice-row, .alert-row { display: flex; align-items: center; justify-content: space-between; gap: 8px; padding: 10px 0; border-bottom: 1px solid #f0f2f5; cursor: pointer; }
.notice-row:last-child, .alert-row:last-child { border-bottom: none; }
.notice-title { font-size: 14px; }
.sku-id { color: #9aa0a6; font-size: 11px; }
.notice-price { display: flex; align-items: center; gap: 8px; margin-top: 4px; font-size: 13px; }
.old { color: #9aa0a6; text-decoration: line-through; }
.arrow { color: #c3c8cf; }
.new { color: #ff2d55; font-weight: 700; }
.save-tag { color: #ff2d55; background: #fff0f2; padding: 2px 8px; border-radius: 999px; font-size: 12px; }
.flat-tag { color: #57606a; background: #f0f2f5; padding: 2px 8px; border-radius: 999px; font-size: 12px; }
.dot { width: 8px; height: 8px; border-radius: 50%; background: #ff2d55; flex: none; }
.muted { color: #9aa0a6; }

.watch-form { display: flex; gap: 8px; }
.input { flex: 1; border: 1px solid #e2e5ea; border-radius: 10px; padding: 9px 12px; font-size: 13px; outline: none; }
.input.small { flex: 0 0 108px; }
.btn { border: none; border-radius: 10px; padding: 9px 18px; font-size: 13px; cursor: pointer; }
.btn.primary { background: #ff2d55; color: #fff; }
.btn:disabled { opacity: .5; cursor: default; }
.mini-btn { border: 1px solid #e2e5ea; background: #fff; border-radius: 8px; padding: 5px 10px; font-size: 12px; cursor: pointer; flex: none; }
.mini-btn.danger { color: #ff2d55; }
.err { color: #ff2d55; font-size: 12px; margin-top: 8px; }

.history-card { background: #fafbfc; border-radius: 10px; padding: 12px; }
.history-main { display: flex; align-items: center; gap: 10px; flex-wrap: wrap; }
.history-summary { margin-top: 8px; font-size: 12px; color: #57606a; }

.toast { position: fixed; left: 50%; bottom: 48px; transform: translateX(-50%); background: rgba(0,0,0,.78); color: #fff; padding: 10px 18px; border-radius: 999px; font-size: 13px; z-index: 99; }
.skeleton { background: linear-gradient(90deg, #f0f2f5 25%, #e5e8ec 37%, #f0f2f5 63%); background-size: 400% 100%; animation: shimmer 1.4s infinite; border-radius: 14px; }
@keyframes shimmer { 0% { background-position: 100% 0; } 100% { background-position: 0 0; } }
</style>
