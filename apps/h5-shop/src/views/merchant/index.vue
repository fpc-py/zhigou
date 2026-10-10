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

    <!-- 经营预警 -->
    <div class="section warn-sec">
      <div class="sec-title">经营预警 <span class="warn-badge">{{ warnTotal }}</span></div>
      <div v-if="warnings.lowStock.length" class="warn-group">
        <div class="warn-label">低库存 SKU（余量 ≤ 10）</div>
        <div v-for="s in warnings.lowStock" :key="s.skuId" class="warn-row">
          <span class="warn-dot low" />
          <div class="warn-main">
            <div class="warn-name">SKU {{ s.skuId }}</div>
            <div class="warn-sub">剩余 {{ s.available }} 件，建议补货</div>
          </div>
          <span class="warn-tag low">低库存</span>
        </div>
      </div>
      <div v-if="warnings.negative.length" class="warn-group">
        <div class="warn-label">近期差评（rating ≤ 3）</div>
        <div v-for="r in warnings.negative" :key="r.spuId + r.createTime" class="warn-row">
          <span class="warn-dot neg" />
          <div class="warn-main">
            <div class="warn-name">SPU {{ r.spuId }} · {{ r.rating }} 星</div>
            <div class="warn-sub">{{ clip(r.content) }}</div>
          </div>
          <span class="warn-tag neg">差评</span>
        </div>
      </div>
      <div v-if="!warnings.lowStock.length && !warnings.negative.length" class="empty">暂无预警，经营健康</div>
    </div>

    <!-- 履约异常（待发货 × 缺货预警） -->
    <div class="section fulfill-sec">
      <div class="sec-title">履约异常 <span class="warn-badge">{{ pendingOrders.length }}</span></div>
      <div class="warn-label">待发货订单（PAID）· 共 {{ pendingOrders.length }} 单 · 演示口径</div>
      <div v-if="pendingOrders.length" class="warn-group">
        <div v-for="o in pendingOrders" :key="o.orderId" class="warn-row">
          <span class="warn-dot fulf" />
          <div class="warn-main">
            <div class="warn-name">订单 {{ shortId(o.orderId) }}{{ o.handled ? ' · 已处理' : '' }}</div>
            <div class="warn-sub">{{ skuSummary(o.items) }}</div>
          </div>
          <span class="warn-tag fulf">{{ o.handled ? '已处理' : '待发货' }}</span>
        </div>
      </div>
      <div v-if="!pendingOrders.length" class="empty">暂无待发货订单</div>
      <div class="fulfill-actions">
        <button v-for="a in fulfillActions" :key="a.value" class="fa-btn" :disabled="faLoading"
          @click="doFulfillAction(a.value)">{{ a.label }}</button>
      </div>
      <div v-if="faMsg" class="supply-msg">{{ faMsg }}</div>
      <div class="warn-label" style="margin-top:10px;">最近处理记录</div>
      <div v-if="faRecords.length" class="warn-group">
        <div v-for="r in faRecords" :key="r.id ?? r.createTime" class="warn-row">
          <span class="warn-dot fulf" />
          <div class="warn-main">
            <div class="warn-name">订单 {{ shortId(r.orderId) }} · {{ ACTION_LABELS[r.action] || r.action }}</div>
            <div class="warn-sub">{{ r.reason || '异常订单处理' }} · {{ (r.createTime || '').slice(0, 19) }}</div>
          </div>
          <span class="warn-tag fulf">{{ r.status }}</span>
        </div>
      </div>
      <div v-if="!faRecords.length" class="empty">暂无处理记录</div>
      <div class="fulfill-note">可问 AI 助手：哪些订单会缺货卡单、如何处理（补货/拆分/延期）</div>
    </div>

    <!-- 补货中心（供应链） -->
    <div class="section supply-sec">
      <div class="sec-title">补货中心</div>
      <div class="warn-label">低库存自动补货 · 演示口径（≤10 件补至 50 件）</div>
      <div v-if="supplyLow.length" class="warn-group">
        <div v-for="s in supplyLow" :key="s.skuId" class="warn-row">
          <span class="warn-dot low" />
          <div class="warn-main">
            <div class="warn-name">SKU {{ s.skuId }}</div>
            <div class="warn-sub">剩余 {{ s.available }} 件，低于安全库存</div>
          </div>
          <span class="warn-tag low">需补货</span>
        </div>
      </div>
      <div v-if="!supplyLow.length" class="empty">暂无低库存 SKU，库存健康</div>
      <button class="supply-btn" :disabled="supplyLoading" @click="runAutoReplenish">
        {{ supplyLoading ? '补货执行中…' : '一键自动补货' }}
      </button>
      <div v-if="supplyMsg" class="supply-msg">{{ supplyMsg }}</div>
      <div class="warn-label" style="margin-top:10px;">最近补货记录</div>
      <div v-if="supplyRecords.length" class="warn-group">
        <div v-for="r in supplyRecords" :key="r.id ?? r.createTime" class="warn-row">
          <span class="warn-dot" :class="r.triggerType === 'AUTO' ? 'fulf' : 'low'" />
          <div class="warn-main">
            <div class="warn-name">SKU {{ r.skuId }} · {{ r.beforeQty }} → {{ r.afterQty }}（+{{ r.addQty }}）</div>
            <div class="warn-sub">{{ r.triggerType === 'AUTO' ? '自动补货' : '手动补货' }} · {{ (r.createTime || '').slice(0, 19) }}</div>
          </div>
          <span class="warn-tag" :class="r.triggerType === 'AUTO' ? 'fulf' : 'low'">{{ r.triggerType }}</span>
        </div>
      </div>
      <div v-if="!supplyRecords.length" class="empty">暂无补货记录</div>
    </div>

    <!-- 经营大脑：销量预测 + 智能选品 -->
    <div class="section brain-sec">
      <div class="sec-title">经营大脑 · 销量预测 <span class="brain-badge">演示</span></div>
      <div class="brain-tabs">
        <span class="brain-tab" :class="{ on: fcDays === 7 }" @click="switchDays(7)">7 天</span>
        <span class="brain-tab" :class="{ on: fcDays === 30 }" @click="switchDays(30)">30 天</span>
      </div>
      <div v-if="forecast.length" class="fc-list">
        <div v-for="f in forecast" :key="f.skuId" class="fc-row">
          <div class="fc-main">
            <div class="fc-name">{{ f.productName }} <span class="fc-hot" :class="f.hotLevel === '热销' ? 'up' : f.hotLevel === '走弱' ? 'down' : 'mid'">{{ f.hotLevel }}</span></div>
            <div class="fc-sub">库存 {{ f.currentStock }} · 近 7 日 {{ f.last7Total }} 件 · 趋势 {{ f.trendPct }}%</div>
          </div>
          <div class="fc-right">
            <div class="fc-qty">≈{{ f.forecastQty }} 件</div>
            <div class="fc-suggest">建议备货 +{{ f.suggestStock }}</div>
          </div>
        </div>
      </div>
      <div v-else class="empty">{{ fcLoading ? '预测计算中…' : '暂无预测数据' }}</div>

      <div class="warn-label" style="margin-top:12px;">智能选品建议</div>
      <div v-if="selection.length" class="sel-list">
        <div v-for="(s, i) in selection" :key="s.skuId" class="sel-row">
          <span class="rank" :class="{ top: i < 2 }">{{ i + 1 }}</span>
          <div class="sel-main">
            <div class="sel-name">{{ s.productName }} <span class="fc-hot" :class="s.hotLevel === '热销' ? 'up' : s.hotLevel === '走弱' ? 'down' : 'mid'">{{ s.hotLevel }}</span></div>
            <div class="sel-reason">{{ s.reason }}</div>
          </div>
        </div>
      </div>
      <div v-else class="empty">暂无选品建议</div>
      <div class="brain-note">数据底座 product-service daily_sales · 演示算法，正式版接入时序模型与促销日历</div>
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
import {
  getMerchantOverview,
  getMerchantWarnings,
  getMerchantFulfillment,
  getReplenishRecords,
  autoReplenish,
  runFulfillmentAction,
  getFulfillmentActions,
  getMerchantForecast,
  getMerchantSelection,
  type ForecastItem,
  type SelectionItem,
  ACTION_LABELS,
  type PendingOrder,
  type FulfillItem,
  type ReplenishRecord,
  type FulfillmentActionRecord,
} from '@/api/merchant'

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

interface Warnings { lowStock: { skuId: number; available: number }[]; negative: { spuId: string; rating: number; content: string; createTime?: string }[] }
const warnings = ref<Warnings>({ lowStock: [], negative: [] })
const warnTotal = computed(() => warnings.value.lowStock.length + warnings.value.negative.length)
const clip = (s: string) => (s || '').length > 26 ? s.slice(0, 26) + '…' : (s || '')

const pendingOrders = ref<PendingOrder[]>([])
const fulfillActions = [
  { value: 'SPLIT', label: '拆分发货' },
  { value: 'DELAY', label: '延期发货' },
  { value: 'REPLENISH', label: '补货后发货' },
  { value: 'OFF_SHELF', label: '下架停单' },
]
const faLoading = ref(false)
const faMsg = ref('')
const faRecords = ref<FulfillmentActionRecord[]>([])

async function doFulfillAction(action: string) {
  faLoading.value = true
  faMsg.value = ''
  try {
    const ids = pendingOrders.value.map((o) => o.orderId)
    if (!ids.length) {
      faMsg.value = '暂无待发货订单可处理'
      return
    }
    const done = await runFulfillmentAction(action, ids)
    faMsg.value = `${ACTION_LABELS[action] || action}执行完成：${done ?? 0} 单已写入处理记录`
    await loadFulfill()
  } catch (e) {
    faMsg.value = '处理失败，请稍后再试'
  } finally {
    faLoading.value = false
  }
}

async function loadFulfill() {
  try {
    const f = await getMerchantFulfillment()
    if (f) pendingOrders.value = f
  } catch (e) {
    // 保持空态
  }
  try {
    const r = await getFulfillmentActions(10)
    if (r) faRecords.value = r
  } catch (e) {
    // 保持空态
  }
}
const forecast = ref<ForecastItem[]>([])
const selection = ref<SelectionItem[]>([])
const fcDays = ref(7)
const fcLoading = ref(false)

async function switchDays(days: number) {
  fcDays.value = days
  fcLoading.value = true
  try {
    const d = await getMerchantForecast(days)
    if (d) forecast.value = d
  } catch (e) {
    // 保持空态
  } finally {
    fcLoading.value = false
  }
}

async function loadBrain() {
  fcLoading.value = true
  try {
    const [f, s] = await Promise.all([getMerchantForecast(7), getMerchantSelection()])
    if (f) forecast.value = f
    if (s) selection.value = s
  } catch (e) {
    // 保持空态
  } finally {
    fcLoading.value = false
  }
}

const supplyLow = ref<{ skuId: number; available: number }[]>([])
const supplyRecords = ref<ReplenishRecord[]>([])
const supplyLoading = ref(false)
const supplyMsg = ref('')

async function runAutoReplenish() {
  supplyLoading.value = true
  supplyMsg.value = ''
  try {
    const done = await autoReplenish(10, 50)
    if (done && done.length) {
      supplyMsg.value = `自动补货完成：${done.length} 个 SKU 已补货（${done.map((r) => 'SKU' + r.skuId).join('、')}）`
    } else {
      supplyMsg.value = '暂无低库存 SKU，无需补货'
    }
    await Promise.all([loadWarnings(), loadSupply()])
  } catch (e) {
    supplyMsg.value = '补货失败，请稍后再试'
  } finally {
    supplyLoading.value = false
  }
}
const shortId = (id: string) => String(id).slice(-8)
const skuSummary = (items: FulfillItem[] | undefined) =>
  (items || []).map((i) => `${i.skuName} ×${i.count}`).join('、') || '无商品'

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
  await loadWarnings()
  await loadFulfill()
  await loadSupply()
  await loadBrain()
})

async function loadWarnings() {
  try {
    const w = await getMerchantWarnings()
    if (w) warnings.value = w
  } catch (e) {
    // 预警区块保持空态
  }
}

async function loadSupply() {
  try {
    const w = await getMerchantWarnings()
    if (w) supplyLow.value = w.lowStock || []
  } catch (e) {
    // 保持空态
  }
  try {
    const r = await getReplenishRecords(10)
    if (r) supplyRecords.value = r
  } catch (e) {
    // 保持空态
  }
}
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
.warn-sec { border: 1px solid #FFE3E5; }
.warn-badge { background: #E5484D; color: #fff; font-size: 11px; border-radius: 10px; padding: 1px 7px; margin-left: 4px; vertical-align: 1px; }
.warn-group { margin-bottom: 8px; }
.warn-label { font-size: 12px; color: #888; margin: 6px 0 4px; }
.warn-row { display: flex; align-items: center; gap: 10px; padding: 7px 0; border-bottom: 1px solid #f5f6f8; }
.warn-row:last-child { border-bottom: none; }
.warn-dot { width: 8px; height: 8px; border-radius: 50%; flex-shrink: 0; }
.warn-dot.low { background: #F5A623; }
.warn-dot.neg { background: #E5484D; }
.warn-main { flex: 1; min-width: 0; }
.warn-name { font-size: 13px; color: #333; }
.warn-sub { font-size: 11px; color: #999; margin-top: 2px; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
.warn-tag { font-size: 10px; border-radius: 4px; padding: 2px 6px; flex-shrink: 0; }
.warn-tag.low { background: #FEF3E2; color: #B7791F; }
.warn-tag.neg { background: #FFE9E9; color: #C03038; }
.fulfill-sec { border: 1px solid #FFE9C9; }
.warn-dot.fulf { background: #F5A623; }
.warn-tag.fulf { background: #FEF3E2; color: #B7791F; }
.fulfill-note { font-size: 11px; color: #B7791F; margin-top: 6px; background: #FFFBF0; border-radius: 8px; padding: 6px 8px; }
.supply-sec { border: 1px solid #D6E4FF; }
.supply-btn { display: block; width: 100%; margin-top: 10px; padding: 10px 0; border: none; border-radius: 10px;
  background: linear-gradient(135deg, #3B82F6, #6366F1); color: #fff; font-size: 14px; font-weight: 600; }
.supply-btn:disabled { opacity: 0.6; }
.supply-msg { margin-top: 8px; font-size: 12px; color: #2563EB; background: #EFF6FF; border-radius: 8px; padding: 6px 8px; }
.fulfill-actions { display: flex; gap: 8px; margin-top: 10px; flex-wrap: wrap; }
.fa-btn { flex: 1; min-width: 72px; padding: 8px 4px; border: 1px solid #F5A623; border-radius: 8px;
  background: #fff; color: #B7791F; font-size: 12px; font-weight: 600; }
.fa-btn:disabled { opacity: 0.6; }
</style>

.brain-sec { border: 1px solid #D6E4FF; }
.brain-badge { background: #3B82F6; color: #fff; font-size: 10px; border-radius: 4px; padding: 1px 5px; margin-left: 4px; vertical-align: 1px; }
.brain-tabs { display: flex; gap: 8px; margin-bottom: 10px; }
.brain-tab { flex: 1; text-align: center; padding: 6px 0; border-radius: 8px; background: #F0F1F4; color: #888; font-size: 12px; cursor: pointer; }
.brain-tab.on { background: #3B82F6; color: #fff; font-weight: 600; }
.fc-list { border-top: 1px solid #f0f1f4; }
.fc-row { display: flex; align-items: center; gap: 10px; padding: 8px 0; border-bottom: 1px solid #f5f6f8; }
.fc-row:last-child { border-bottom: none; }
.fc-main { flex: 1; min-width: 0; }
.fc-name { font-size: 13px; color: #333; display: flex; align-items: center; gap: 6px; }
.fc-hot { font-size: 10px; border-radius: 4px; padding: 1px 5px; }
.fc-hot.up { background: #E6F7EC; color: #18794E; }
.fc-hot.down { background: #FFE9E9; color: #C03038; }
.fc-hot.mid { background: #F0F1F4; color: #555; }
.fc-sub { font-size: 11px; color: #999; margin-top: 2px; }
.fc-right { text-align: right; flex-shrink: 0; }
.fc-qty { font-size: 15px; font-weight: 700; color: #2563EB; }
.fc-suggest { font-size: 10px; color: #B7791F; margin-top: 2px; }
.sel-list { border-top: 1px solid #f0f1f4; }
.sel-row { display: flex; align-items: flex-start; gap: 10px; padding: 8px 0; border-bottom: 1px solid #f5f6f8; }
.sel-row:last-child { border-bottom: none; }
.sel-main { flex: 1; min-width: 0; }
.sel-name { font-size: 13px; color: #333; display: flex; align-items: center; gap: 6px; }
.sel-reason { font-size: 11px; color: #666; margin-top: 2px; line-height: 1.5; }
.brain-note { font-size: 10px; color: #aaa; margin-top: 8px; }
