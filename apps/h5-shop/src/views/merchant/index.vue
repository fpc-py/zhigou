<template>
  <div class="page">
    <!-- 头部 -->
    <div class="header">
      <div class="back" @click="router.back()">‹</div>
      <div class="title">商家中心</div>
      <div class="placeholder" />
    </div>

    <!-- 演示标注 -->
    <div class="demo-tip">演示环境：单商家市场，经营数据为平台聚合口径</div>

    <!-- 核心指标 -->
    <div class="cards">
      <div class="card main">
        <div class="label">累计销售额（元）</div>
        <div class="value">¥{{ (overview.totalSalesFen / 100).toFixed(2) }}</div>
        <div class="sub">累计订单 {{ overview.totalOrders }} 单</div>
      </div>
      <div class="card">
        <div class="label">今日订单</div>
        <div class="value">{{ overview.todayOrders }}</div>
        <div class="sub">¥{{ (overview.todaySalesFen / 100).toFixed(2) }}</div>
      </div>
      <div class="card">
        <div class="label">待处理售后</div>
        <div class="value warn">{{ overview.pendingAfterSale }}</div>
        <div class="sub">演示口径</div>
      </div>
    </div>

    <!-- 状态分布 -->
    <div class="section">
      <div class="sec-title">订单状态分布</div>
      <div class="dist" v-if="statusRows.length">
        <div v-for="r in statusRows" :key="r.status" class="dist-row">
          <span class="dist-name">{{ statusLabel(r.status) }}</span>
          <div class="dist-bar">
            <div class="dist-fill" :style="{ width: r.pct + '%' }" />
          </div>
          <span class="dist-count">{{ r.count }} 单</span>
        </div>
      </div>
      <div class="empty" v-else>暂无订单数据</div>
    </div>

    <!-- 热销榜 -->
    <div class="section">
      <div class="sec-title">热销商品 Top{{ hotList.length }}</div>
      <div v-if="hotList.length" class="hot">
        <div v-for="(h, i) in hotList" :key="h.spuId" class="hot-row">
          <span class="rank" :class="{ top: i < 3 }">{{ i + 1 }}</span>
          <div class="hot-main">
            <div class="hot-name">{{ h.spuName }}</div>
            <div class="hot-sub">售出 {{ h.soldCount }} 件 · ¥{{ (h.salesFen / 100).toFixed(2) }}</div>
          </div>
        </div>
      </div>
      <div class="empty" v-else>暂无销售数据</div>
    </div>

    <!-- AI 经营助手 -->
    <button class="ai-btn" @click="goChat">
      <span class="ai-icon">✦</span>
      <div class="ai-txt">
        <div class="ai-title">AI 经营助手</div>
        <div class="ai-sub">问销量、看报表、查热销（经营概览演示）</div>
      </div>
      <span class="ai-arrow">›</span>
    </button>

    <div class="footnote">数据来自 order-service /order/stats/overview · 生成于 {{ overview.generatedAt }}</div>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { getMerchantOverview } from '@/api/merchant'

const router = useRouter()

interface HotSpu { spuId: number; spuName: string; soldCount: number; salesFen: number }
interface Overview {
  totalOrders: number; totalSalesFen: number; todayOrders: number; todaySalesFen: number
  statusDist: Record<string, number>; hotSpus: HotSpu[]; pendingAfterSale: number; generatedAt: string
}
const overview = ref<Overview>({
  totalOrders: 0, totalSalesFen: 0, todayOrders: 0, todaySalesFen: 0,
  statusDist: {}, hotSpus: [], pendingAfterSale: 0, generatedAt: '',
})

const LABELS: Record<string, string> = {
  INIT: '待支付', PAID: '已支付', SHIPPED: '已发货', COMPLETED: '已完成',
  CLOSED: '已关闭', REFUNDING: '退款中', REFUNDED: '已退款',
}
const statusLabel = (s: string) => LABELS[s] ?? s

const statusRows = computed(() => {
  const total = Object.values(overview.value.statusDist || {}).reduce((a, b) => a + b, 0)
  return Object.entries(overview.value.statusDist || {})
    .map(([status, count]) => ({ status, count, pct: total ? Math.round((count / total) * 100) : 0 }))
    .sort((a, b) => b.count - a.count)
})
const hotList = computed(() => overview.value.hotSpus || [])

function goChat() {
  router.push({ path: '/chat', query: { q: '帮我看看今天的经营情况：订单、销售额和热销商品' } })
}

onMounted(async () => {
  try {
    const data = await getMerchantOverview()
    if (data) overview.value = data
  } catch (e) {
    // 保持空态；页面可读
  }
})
</script>

<style scoped>
.page { max-width: 414px; margin: 0 auto; min-height: 100vh; background: #f5f6f8; padding-bottom: 32px; }
.header { display: flex; align-items: center; justify-content: space-between; padding: 14px 16px; background: #fff; position: sticky; top: 0; z-index: 10; }
.back { font-size: 26px; color: #333; width: 32px; cursor: pointer; }
.title { font-size: 17px; font-weight: 600; }
.placeholder { width: 32px; }
.demo-tip { margin: 12px 16px 0; padding: 8px 12px; background: #FFF7E8; color: #B7791F; font-size: 12px; border-radius: 8px; }
.cards { display: grid; grid-template-columns: repeat(3, 1fr); gap: 10px; margin: 14px 16px; }
.card { background: #fff; border-radius: 12px; padding: 14px 12px; }
.card.main { grid-column: span 3; background: linear-gradient(135deg, #4C5CFF, #7A5CFF); color: #fff; }
.card.main .sub { color: rgba(255,255,255,.85); }
.label { font-size: 12px; color: #888; }
.card.main .label { color: rgba(255,255,255,.85); }
.value { font-size: 22px; font-weight: 700; margin: 6px 0 4px; }
.card.main .value { font-size: 30px; }
.sub { font-size: 11px; color: #aaa; }
.warn { color: #E5484D; }
.section { margin: 14px 16px; background: #fff; border-radius: 12px; padding: 14px; }
.sec-title { font-size: 14px; font-weight: 600; margin-bottom: 12px; }
.dist-row { display: flex; align-items: center; gap: 8px; margin-bottom: 8px; }
.dist-name { width: 52px; font-size: 12px; color: #555; }
.dist-bar { flex: 1; height: 8px; background: #f0f1f4; border-radius: 4px; overflow: hidden; }
.dist-fill { height: 100%; background: #4C5CFF; border-radius: 4px; }
.dist-count { width: 46px; font-size: 12px; color: #333; text-align: right; }
.empty { text-align: center; color: #bbb; font-size: 13px; padding: 18px 0; }
.hot-row { display: flex; align-items: center; gap: 10px; padding: 8px 0; border-bottom: 1px solid #f5f6f8; }
.hot-row:last-child { border-bottom: none; }
.rank { width: 22px; height: 22px; border-radius: 6px; background: #f0f1f4; color: #888; font-size: 12px; display: flex; align-items: center; justify-content: center; flex-shrink: 0; }
.rank.top { background: #FFD84D; color: #7A5B00; font-weight: 700; }
.hot-main { flex: 1; }
.hot-name { font-size: 13px; color: #333; }
.hot-sub { font-size: 11px; color: #999; margin-top: 2px; }
.ai-btn { display: flex; align-items: center; gap: 12px; margin: 14px 16px; padding: 14px; background: linear-gradient(135deg, #6EE7B7, #34D399); border: none; border-radius: 14px; width: calc(100% - 32px); color: #065F46; text-align: left; cursor: pointer; }
.ai-icon { font-size: 22px; }
.ai-txt { flex: 1; }
.ai-title { font-size: 15px; font-weight: 700; }
.ai-sub { font-size: 11px; opacity: .85; margin-top: 2px; }
.ai-arrow { font-size: 22px; }
.footnote { text-align: center; color: #bbb; font-size: 11px; margin-top: 8px; padding: 0 16px; word-break: break-all; }
</style>
