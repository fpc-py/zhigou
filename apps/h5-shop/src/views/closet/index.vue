<template>
  <div class="page closet-page">
    <!-- 头部 -->
    <header class="closet-head">
      <div>
        <p class="head-title">我的衣橱</p>
        <p class="head-sub"><Icon name="ai" size="xs" /> 小智已记住 {{ items.length }} 件单品</p>
      </div>
      <button class="add-btn" @click="openAdd"><Icon name="plus" /></button>
    </header>

    <!-- Tab：衣物 / 穿搭 / 家居 -->
    <div class="tabs">
      <span v-for="t in tabs" :key="t.k" class="tab" :class="{ on: tab === t.k }" @click="switchTab(t.k)">{{ t.label }}</span>
    </div>

    <!-- ===== 衣物 Tab ===== -->
    <template v-if="tab === 'items'">
      <section class="stats card">
        <div class="stat"><b>{{ items.length }}</b><span>收录件数</span></div>
        <div class="stat"><b>{{ totalWear }}</b><span>累计穿着</span></div>
        <div class="stat"><b>{{ utilRate }}%</b><span>利用率</span></div>
      </section>

      <div class="chips">
        <span v-for="c in catChips" :key="c" class="chip" :class="{ on: cat === c }" @click="switchCat(c)">{{ c }}</span>
      </div>

      <section class="item-list">
        <div v-for="it in filtered" :key="it.id" class="item card">
          <span class="it-emoji">{{ emoji(it.category) }}</span>
          <div class="it-info">
            <p class="it-name">{{ it.name }}</p>
            <p class="it-tags">{{ it.category }} · {{ it.season }}<template v-if="it.color"> · {{ it.color }}</template></p>
          </div>
          <div class="it-right">
            <span class="wear-badge"><Icon name="check" size="xs" /> {{ it.wearCount }} 次</span>
            <button class="wear-btn-sm" @click="wear(it)">打卡</button>
            <button class="del-btn" @click="remove(it)">移除</button>
          </div>
        </div>
        <p v-if="filtered.length === 0" class="empty">衣橱空空，点击右上角 + 收录第一件单品</p>
      </section>
    </template>

    <!-- ===== 穿搭 Tab ===== -->
    <template v-if="tab === 'outfit'">
      <div class="chips">
        <span v-for="o in occasions" :key="o" class="chip" :class="{ on: occasion === o }" @click="genOutfit(o)">{{ o }}</span>
      </div>

      <section v-if="outfit" class="outfit card">
        <div class="outfit-head">
          <h3 class="sec-title">{{ outfit.occasion }}穿搭</h3>
          <span class="ai-badge"><Icon name="ai" size="xs" /> 小智搭配</span>
        </div>
        <div class="outfit-chips">
          <span v-for="it in outfit.items" :key="it.id" class="o-chip have">
            <Icon name="check" size="xs" /> {{ it.name }}
          </span>
        </div>
        <p class="outfit-note">{{ outfit.note }}</p>
        <button class="wear-btn" @click="toast('生成穿搭海报功能规划中')">一键生成今日海报</button>
      </section>
      <section v-else class="outfit card">
        <p class="empty">选择场合，小智为你搭配今日穿搭</p>
      </section>
    </template>

    <!-- ===== 家居 Tab ===== -->
    <template v-if="tab === 'home'">
      <section v-if="replenish.length" class="replenish card">
        <div class="outfit-head">
          <h3 class="sec-title">需补货 {{ replenish.length }} 项</h3>
          <span class="gap-note">数量≤1 或 7 天内到期</span>
        </div>
        <div class="gap-items">
          <div v-for="a in replenish" :key="a.id" class="gap-item">
            <span class="gap-img">{{ assetEmoji(a.category) }}</span>
            <div class="gap-info">
              <p class="gap-name">{{ a.name }}</p>
              <p class="gap-tag">{{ a.category }} · {{ a.quantity }}{{ a.unit }}<template v-if="a.expireAt"> · {{ a.expireAt.slice(0, 10) }} 到期</template></p>
            </div>
            <button class="gap-go" @click="goProducts">去补货</button>
          </div>
        </div>
      </section>
      <p v-else class="empty card" style="text-align:center;padding:20px 0;">暂无补货需求，家库充足</p>

      <div class="chips">
        <span v-for="c in assetCats" :key="c" class="chip" :class="{ on: assetCat === c }" @click="switchAssetCat(c)">{{ c }}</span>
      </div>
      <section class="item-list">
        <div v-for="a in filteredAssets" :key="a.id" class="item card">
          <span class="it-emoji">{{ assetEmoji(a.category) }}</span>
          <div class="it-info">
            <p class="it-name">{{ a.name }}</p>
            <p class="it-tags">{{ a.category }} · 存量 {{ a.quantity }}{{ a.unit }}<template v-if="a.expireAt"> · {{ a.expireAt.slice(0, 10) }} 到期</template></p>
          </div>
          <span v-if="isReplenish(a)" class="need-badge">需补</span>
        </div>
        <p v-if="filteredAssets.length === 0" class="empty">暂无记录，点击右上角 + 添加家居物品</p>
      </section>
    </template>

    <!-- 添加弹层 -->
    <div v-if="showAdd" class="mask" @click.self="showAdd = false">
      <div class="sheet">
        <h3 class="sheet-title">{{ tab === 'home' ? '添加家居物品' : '收录新衣物' }}</h3>
        <input v-model="form.name" class="input" :placeholder="tab === 'home' ? '物品名称（如 大米5kg）' : '衣物名称（如 燕麦色风衣）'" />
        <div class="form-row">
          <select v-model="form.category" class="input">
            <option v-for="c in tab === 'home' ? assetCats.slice(1) : catChips.slice(1)" :key="c" :value="c">{{ c }}</option>
          </select>
          <select v-model="form.season" v-if="tab !== 'home'" class="input">
            <option v-for="s in seasons" :key="s" :value="s">{{ s }}</option>
          </select>
        </div>
        <div v-if="tab === 'home'" class="form-row">
          <input v-model.number="form.quantity" class="input" type="number" min="1" placeholder="数量" />
          <input v-model="form.unit" class="input" placeholder="单位（件/瓶/袋）" />
        </div>
        <input v-if="tab === 'home'" v-model="form.expireAt" class="input" type="date" placeholder="到期日（可选）" />
        <input v-if="tab !== 'home'" v-model="form.color" class="input" placeholder="颜色（可选）" />
        <div class="sheet-actions">
          <button class="btn-cancel" @click="showAdd = false">取消</button>
          <button class="btn-ok" @click="submitAdd">保存</button>
        </div>
      </div>
    </div>

    <div class="planning-note">穿搭推荐为规则引擎（演示，非 AI 模型）；衣物识别/图像导入规划中</div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue';
import { useRouter } from 'vue-router';
import { showToast } from '@/utils';
import Icon from '@/components/Icon.vue';
import {
  getClosetItems, addClosetItem, wearClosetItem, deleteClosetItem,
  recommendOutfit, getHomeAssets, addHomeAsset, getReplenishList,
} from '@/api/closet';
import type { ClosetItem, HomeAsset, OutfitRecommend } from '@/api/closet';

const router = useRouter();
const tabs = [
  { k: 'items', label: '衣物' },
  { k: 'outfit', label: '穿搭' },
  { k: 'home', label: '家居' },
];
const tab = ref<'items' | 'outfit' | 'home'>('items');
const catChips = ['全部', '上装', '下装', '外套', '鞋履', '配饰'];
const assetCats = ['全部', '食品', '日用品', '家电', '清洁'];
const seasons = ['四季', '春', '夏', '秋', '冬'];
const occasions = ['通勤', '休闲', '运动', '约会'];

const items = ref<ClosetItem[]>([]);
const cat = ref('全部');
const assets = ref<HomeAsset[]>([]);
const assetCat = ref('全部');
const replenish = ref<HomeAsset[]>([]);
const outfit = ref<OutfitRecommend | null>(null);
const occasion = ref('通勤');

const showAdd = ref(false);
const form = ref<Record<string, any>>({ name: '', category: '', season: '四季', color: '', quantity: 1, unit: '件', expireAt: '' });

const filtered = computed(() => (cat.value === '全部' ? items.value : items.value.filter((i) => i.category === cat.value)));
const filteredAssets = computed(() => (assetCat.value === '全部' ? assets.value : assets.value.filter((a) => a.category === assetCat.value)));
const totalWear = computed(() => items.value.reduce((s, i) => s + (i.wearCount || 0), 0));
const utilRate = computed(() => (items.value.length ? Math.round((items.value.filter((i) => (i.wearCount || 0) > 0).length / items.value.length) * 100) : 0));

function emoji(c: string): string {
  return { 上装: '👕', 下装: '👖', 外套: '🧥', 鞋履: '👟', 配饰: '🧣' }[c] || '👕';
}
function assetEmoji(c: string): string {
  return { 食品: '🍚', 日用品: '🧻', 家电: '🔌', 清洁: '🧴' }[c] || '📦';
}
function isReplenish(a: HomeAsset): boolean {
  return replenish.value.some((r) => r.id === a.id);
}
function toast(msg: string) {
  showToast(msg);
}
function goProducts() {
  router.push('/products');
}

function switchTab(k: string) {
  const kk = k as 'items' | 'outfit' | 'home';
  tab.value = kk;
  if (kk === 'items') loadItems();
  if (kk === 'home') loadHome();
  if (kk === 'outfit' && !outfit.value) genOutfit('通勤');
}
function switchCat(c: string) {
  cat.value = c;
}
function switchAssetCat(c: string) {
  assetCat.value = c;
}

async function loadItems() {
  try {
    items.value = await getClosetItems();
  } catch {
    items.value = [];
  }
}
async function loadHome() {
  try {
    const [as, rp] = await Promise.all([getHomeAssets(), getReplenishList()]);
    assets.value = as;
    replenish.value = rp;
  } catch {
    assets.value = [];
    replenish.value = [];
  }
}
async function genOutfit(o: string) {
  occasion.value = o;
  try {
    outfit.value = await recommendOutfit(o);
  } catch (e: any) {
    toast(e.message || '衣橱暂无衣物，先添加再生成穿搭');
    outfit.value = null;
  }
}
async function wear(it: ClosetItem) {
  await wearClosetItem(it.id);
  toast('已打卡，穿着 +1');
  loadItems();
}
async function remove(it: ClosetItem) {
  await deleteClosetItem(it.id);
  toast('已移除该衣物');
  loadItems();
}

function openAdd() {
  form.value = tab.value === 'home'
    ? { name: '', category: '食品', quantity: 1, unit: '件', expireAt: '' }
    : { name: '', category: '上装', season: '四季', color: '' };
  showAdd.value = true;
}
async function submitAdd() {
  if (!form.value.name) {
    toast('请填写名称');
    return;
  }
  try {
    if (tab.value === 'home') {
      await addHomeAsset({
        name: form.value.name,
        category: form.value.category,
        quantity: Number(form.value.quantity) || 1,
        unit: form.value.unit || '件',
        expireAt: form.value.expireAt || undefined,
      });
      toast('已添加家居物品');
      loadHome();
    } else {
      await addClosetItem({
        name: form.value.name,
        category: form.value.category,
        season: form.value.season || '四季',
        color: form.value.color || undefined,
      });
      toast('已收录新衣物');
      loadItems();
    }
    showAdd.value = false;
  } catch (e: any) {
    toast(e.message || '保存失败');
  }
}

onMounted(() => {
  loadItems();
});
</script>

<style scoped>
.closet-page { min-height: 100vh; padding: 16px 14px 30px; }
.closet-head { display: flex; align-items: center; justify-content: space-between; padding: 4px 2px 14px; }
.head-title { font-size: 19px; font-weight: 800; }
.head-sub { font-size: 11.5px; color: var(--ink-3); margin-top: 3px; display: flex; align-items: center; gap: 4px; }
.head-sub svg { color: var(--brand); }
.add-btn { width: 34px; height: 34px; border-radius: 12px; background: var(--brand); color: #fff; display: flex; align-items: center; justify-content: center; }
.card { background: var(--card); border-radius: var(--radius); padding: 16px; margin-bottom: 12px; }
.tabs { display: flex; background: var(--bg); border-radius: 12px; padding: 3px; margin-bottom: 12px; }
.tab { flex: 1; text-align: center; font-size: 13px; font-weight: 600; color: var(--ink-3); padding: 8px 0; border-radius: 10px; cursor: pointer; }
.tab.on { background: var(--card); color: var(--brand); box-shadow: 0 1px 4px rgba(0, 0, 0, 0.06); }
.stats { display: flex; }
.stat { flex: 1; text-align: center; }
.stat + .stat { border-left: 1px solid var(--line); }
.stat b { display: block; font-size: 22px; color: var(--brand); line-height: 1.2; }
.stat span { font-size: 11px; color: var(--ink-3); }
.chips { display: flex; gap: 8px; overflow-x: auto; padding: 2px 0 10px; }
.chip { flex-shrink: 0; font-size: 12px; font-weight: 600; color: var(--ink-2); background: var(--card); border: 1px solid var(--line); border-radius: 999px; padding: 6px 13px; cursor: pointer; }
.chip.on { color: #fff; background: var(--brand); border-color: var(--brand); }
.item-list { display: flex; flex-direction: column; gap: 10px; }
.item { display: flex; align-items: center; gap: 10px; padding: 12px; }
.it-emoji { width: 44px; height: 44px; border-radius: 10px; background: var(--bg); display: flex; align-items: center; justify-content: center; font-size: 22px; }
.it-info { flex: 1; min-width: 0; }
.it-name { font-size: 13.5px; font-weight: 600; }
.it-tags { font-size: 11px; color: var(--ink-3); margin-top: 2px; }
.it-right { display: flex; align-items: center; gap: 6px; }
.wear-badge { display: inline-flex; align-items: center; gap: 2px; font-size: 10.5px; color: var(--mint); background: var(--mint-soft); border-radius: 999px; padding: 4px 8px; }
.wear-btn-sm { font-size: 11px; font-weight: 600; color: var(--brand); background: var(--brand-soft); border-radius: 999px; padding: 6px 10px; }
.del-btn { font-size: 11px; color: var(--ink-3); background: none; padding: 4px 2px; }
.empty { font-size: 12px; color: var(--ink-3); text-align: center; padding: 16px 0; }
.outfit-head { display: flex; align-items: center; justify-content: space-between; }
.sec-title { font-size: 14px; font-weight: 700; }
.ai-badge { display: inline-flex; align-items: center; gap: 4px; font-size: 10.5px; font-weight: 700; color: var(--brand); background: var(--brand-soft); padding: 4px 9px; border-radius: 999px; }
.outfit-chips { display: flex; flex-wrap: wrap; gap: 8px; margin-top: 12px; }
.o-chip { display: inline-flex; align-items: center; gap: 4px; font-size: 11.5px; border-radius: 999px; padding: 6px 11px; color: var(--mint); background: var(--mint-soft); }
.outfit-note { font-size: 10.5px; color: var(--ink-3); margin-top: 10px; }
.wear-btn { margin-top: 14px; width: 100%; height: 42px; border-radius: 999px; background: linear-gradient(120deg, var(--brand), #7A6BFF); color: #fff; font-size: 13.5px; font-weight: 600; }
.gap-note { font-size: 10.5px; color: var(--ink-3); }
.gap-items { display: flex; flex-direction: column; gap: 10px; margin-top: 12px; }
.gap-item { display: flex; align-items: center; gap: 10px; background: var(--bg); border-radius: 12px; padding: 10px; }
.gap-img { width: 44px; height: 44px; border-radius: 10px; background: #fff; display: flex; align-items: center; justify-content: center; font-size: 22px; }
.gap-info { flex: 1; }
.gap-name { font-size: 13px; font-weight: 600; }
.gap-tag { font-size: 10.5px; color: var(--ink-3); margin-top: 2px; }
.gap-go { font-size: 11.5px; font-weight: 600; color: var(--brand); background: var(--brand-soft); border-radius: 999px; padding: 7px 13px; }
.need-badge { font-size: 10.5px; font-weight: 700; color: var(--accent); background: var(--accent-soft); border-radius: 999px; padding: 4px 9px; }
.planning-note { text-align: center; font-size: 10.5px; color: var(--ink-3); padding: 6px 0; }
.mask { position: fixed; inset: 0; background: rgba(0, 0, 0, 0.45); display: flex; align-items: flex-end; z-index: 99; }
.sheet { width: 100%; background: var(--card); border-radius: 16px 16px 0 0; padding: 18px 16px 24px; max-height: 80vh; overflow-y: auto; }
.sheet-title { font-size: 15px; font-weight: 700; margin-bottom: 12px; }
.input { width: 100%; box-sizing: border-box; height: 40px; border: 1px solid var(--line); border-radius: 10px; padding: 0 12px; font-size: 13px; background: var(--bg); margin-bottom: 10px; }
.form-row { display: flex; gap: 10px; }
.form-row .input { flex: 1; }
.sheet-actions { display: flex; gap: 10px; margin-top: 4px; }
.btn-cancel { flex: 1; height: 42px; border-radius: 999px; background: var(--bg); color: var(--ink-2); font-size: 13.5px; font-weight: 600; }
.btn-ok { flex: 1; height: 42px; border-radius: 999px; background: var(--brand); color: #fff; font-size: 13.5px; font-weight: 600; }
</style>
