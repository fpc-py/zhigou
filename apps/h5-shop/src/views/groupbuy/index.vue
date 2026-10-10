<template>
  <div class="page">
    <!-- 顶栏 -->
    <header class="topbar">
      <button class="icon-btn" @click="$router.back()"><Icon name="back" /></button>
      <h1>拼团 · 社交购物</h1>
      <span class="topbar-tag">2 人成团起</span>
    </header>

    <main class="body">
      <!-- 进行中活动 -->
      <section v-if="loading">
        <div v-for="i in 3" :key="i" class="act-card">
          <div class="act-img skeleton" />
          <div class="act-main"><div class="skeleton line" style="width: 70%" /><div class="skeleton line" style="width: 45%" /></div>
        </div>
      </section>

      <EmptyState v-else-if="!activities.length" icon="gift" text="暂无进行中的拼团活动" />

      <template v-else>
        <div v-for="a in activities" :key="a.id" class="act-card">
          <img v-if="a.imageUrl" :src="a.imageUrl" class="act-img" alt="" loading="lazy" />
          <div v-else class="act-img act-img-placeholder" />
          <div class="act-main">
            <h3 class="act-title">{{ a.title }}</h3>
            <div class="act-price">
              <span class="group-price">拼团价 ¥{{ (a.groupPrice / 100).toFixed(2) }}</span>
              <span class="solo-price">单人价 ¥{{ (a.soloPrice / 100).toFixed(2) }}</span>
              <span class="save-tag">立省 ¥{{ ((a.soloPrice - a.groupPrice) / 100).toFixed(2) }}</span>
            </div>
            <div class="act-meta">{{ a.groupSize }} 人成团 · {{ a.limitMinutes }} 小时限时</div>

            <!-- 可加入的团 -->
            <div v-if="a.openGroups.length" class="open-groups">
              <div v-for="g in a.openGroups.slice(0, 3)" :key="g.groupId" class="open-row">
                <span class="remain">还差 <b>{{ g.remain }}</b> 人成团</span>
                <span class="member-count">{{ g.memberCount }}/{{ g.targetSize }} 人</span>
                <button class="mini-btn" @click="join(g.groupId, a)">去参团</button>
              </div>
            </div>
            <div v-else class="open-groups empty-tip">暂无在拼的团，快来开团当团长</div>

            <button class="open-btn" @click="openGroup(a)">发起开团 · 享拼团价</button>
          </div>
        </div>
      </template>

      <!-- 我的拼团 -->
      <section v-if="mine.length" class="mine-sec">
        <h2 class="sec-title">我的拼团</h2>
        <div v-for="m in mine" :key="m.groupId" class="mine-row" :class="statusClass(m.status)">
          <div class="mine-main">
            <span class="mine-title">{{ m.activity?.title ?? '拼团' }}</span>
            <span class="mine-progress">{{ m.memberCount }}/{{ m.targetSize }} 人 · 还差 {{ m.remain }} 人</span>
          </div>
          <span class="mine-status">{{ statusText(m.status) }}</span>
          <button v-if="m.status === 'OPEN'" class="mini-btn" @click="copyShare(m.groupId)">邀请好友</button>
        </div>
      </section>
    </main>

    <!-- 弹窗 -->
    <div v-if="modal" class="mask" @click.self="modal = null">
      <div class="sheet">
        <h3>{{ modal.title }}</h3>
        <p class="sheet-desc">{{ modal.desc }}</p>
        <p v-if="modal.price" class="sheet-price">拼团价 ¥{{ modal.price }}</p>
        <div class="sheet-actions">
          <button class="btn ghost" @click="modal = null">再想想</button>
          <button class="btn primary" :disabled="busy" @click="modal.confirm">{{ busy ? '处理中…' : modal.action }}</button>
        </div>
      </div>
    </div>

    <div v-if="toast" class="toast">{{ toast }}</div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue';
import { useRouter } from 'vue-router';
import Icon from '@/components/Icon.vue';
import EmptyState from '@/components/EmptyState.vue';
import {
  getGroupBuyActivities,
  openGroupBuy,
  joinGroupBuy,
  getMyGroupBuys,
  type GroupBuyActivity,
} from '@/api/marketing';

const router = useRouter();
const loading = ref(true);
const busy = ref(false);
const activities = ref<GroupBuyActivity[]>([]);
const mine = ref<Array<Record<string, any>>>([]);
const modal = ref<null | {
  title: string;
  desc: string;
  price?: string;
  action: string;
  confirm: () => Promise<void>;
}>(null);
const toast = ref('');

function showToast(msg: string) {
  toast.value = msg;
  setTimeout(() => (toast.value = ''), 1800);
}

function statusText(s: string) {
  return { OPEN: '拼团中', SUCCESS: '已成团', CLOSED: '未成团' }[s] ?? s;
}
function statusClass(s: string) {
  return { OPEN: 'st-open', SUCCESS: 'st-success', CLOSED: 'st-closed' }[s] ?? '';
}

async function loadAll() {
  loading.value = true;
  const [acts, my] = await Promise.all([getGroupBuyActivities(), getMyGroupBuys()]);
  activities.value = acts;
  mine.value = my;
  loading.value = false;
}

function openGroup(a: GroupBuyActivity) {
  modal.value = {
    title: '发起开团',
    desc: `「${a.title}」${a.groupSize} 人成团，拼团价 ¥${(a.groupPrice / 100).toFixed(2)}。成团后按拼团价支付，超时未成团自动退回原价差额。`,
    price: `¥${(a.groupPrice / 100).toFixed(2)}`,
    action: '确认开团',
    confirm: async () => {
      busy.value = true;
      try {
        const res = await openGroupBuy(a.id);
        modal.value = null;
        if (res) {
          showToast(`开团成功！团号 #${res.id}，邀请好友一起拼`);
          await loadAll();
        } else {
          showToast('开团失败，请稍后再试');
        }
      } catch (e: any) {
        modal.value = null;
        showToast(e?.response?.data?.message || '开团失败，请稍后再试');
      }
      busy.value = false;
    },
  };
}

function join(groupId: string, a: GroupBuyActivity) {
  modal.value = {
    title: '加入拼团',
    desc: `加入团 #${groupId}，与团友一起按拼团价 ¥${(a.groupPrice / 100).toFixed(2)} 购买「${a.title}」。`,
    price: `¥${(a.groupPrice / 100).toFixed(2)}`,
    action: '确认参团',
    confirm: async () => {
      busy.value = true;
      try {
        const res = await joinGroupBuy(groupId);
        modal.value = null;
        if (res) {
          showToast(res.status === 'SUCCESS' ? '🎉 成团成功，快下单吧！' : '参团成功，还差几人成团');
          await loadAll();
        } else {
          showToast('参团失败，可能该团已满员或过期');
        }
      } catch (e: any) {
        modal.value = null;
        showToast(e?.response?.data?.message || '参团失败，可能该团已满员或过期');
      }
      busy.value = false;
    },
  };
}

async function copyShare(groupId: string) {
  const text = `我在智购开了个拼团，快来一起拼！团号 #${groupId}`;
  try {
    await navigator.clipboard.writeText(text);
    showToast('邀请文案已复制，快去分享给好友');
  } catch {
    showToast(text);
  }
}

onMounted(loadAll);
</script>

<style scoped>
.page { min-height: 100vh; background: var(--bg); display: flex; flex-direction: column; }
.topbar {
  flex: none; display: flex; align-items: center; gap: 10px;
  padding: 12px 14px; background: var(--card); border-bottom: 1px solid var(--line);
  position: sticky; top: 0; z-index: 5;
}
.topbar h1 { flex: 1; font-size: 16px; font-weight: 700; margin: 0; }
.topbar-tag { font-size: 10px; color: var(--brand); background: var(--brand-soft); border-radius: 999px; padding: 3px 8px; }
.body { flex: 1; padding: 12px 14px 24px; display: flex; flex-direction: column; gap: 12px; }

.act-card { display: flex; gap: 12px; background: var(--card); border-radius: 14px; padding: 12px; border: 1px solid var(--line); }
.act-img { width: 92px; height: 92px; border-radius: 10px; object-fit: cover; flex: none; background: var(--brand-soft); }
.act-img-placeholder { }
.act-main { flex: 1; min-width: 0; }
.act-title { font-size: 14px; font-weight: 700; margin: 0 0 6px; line-height: 1.35; }
.act-price { display: flex; align-items: baseline; gap: 8px; flex-wrap: wrap; }
.group-price { font-size: 17px; font-weight: 800; color: var(--danger, #e04b2a); }
.solo-price { font-size: 11px; color: var(--ink-3); text-decoration: line-through; }
.save-tag { font-size: 10px; color: #fff; background: var(--danger, #e04b2a); border-radius: 999px; padding: 2px 6px; }
.act-meta { font-size: 11px; color: var(--ink-3); margin-top: 4px; }

.open-groups { margin-top: 8px; display: flex; flex-direction: column; gap: 6px; }
.open-row { display: flex; align-items: center; gap: 8px; font-size: 12px; background: var(--bg); border-radius: 8px; padding: 6px 8px; }
.remain { flex: 1; }
.remain b { color: var(--brand); }
.member-count { color: var(--ink-3); }
.mini-btn { flex: none; font-size: 11.5px; font-weight: 600; color: var(--brand); border: 1px solid var(--brand); background: transparent; border-radius: 999px; padding: 4px 10px; }
.empty-tip { font-size: 11.5px; color: var(--ink-3); background: var(--bg); border-radius: 8px; padding: 6px 8px; }
.open-btn { margin-top: 10px; width: 100%; background: var(--brand); color: #fff; border: 0; border-radius: 10px; font-size: 13px; font-weight: 700; padding: 10px 0; }

.mine-sec { margin-top: 6px; }
.sec-title { font-size: 15px; font-weight: 700; margin: 0 0 8px; }
.mine-row { display: flex; align-items: center; gap: 10px; background: var(--card); border: 1px solid var(--line); border-radius: 12px; padding: 10px 12px; margin-bottom: 8px; }
.mine-main { flex: 1; min-width: 0; display: flex; flex-direction: column; gap: 2px; }
.mine-title { font-size: 13px; font-weight: 600; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
.mine-progress { font-size: 11px; color: var(--ink-3); }
.mine-status { font-size: 12px; font-weight: 700; }
.st-open { color: var(--brand); }
.st-success { color: #16a34a; }
.st-closed { color: var(--ink-3); }

.mask { position: fixed; inset: 0; background: rgba(0,0,0,.45); z-index: 50; display: flex; align-items: flex-end; }
.sheet { width: 100%; background: var(--card); border-radius: 16px 16px 0 0; padding: 18px 16px 22px; }
.sheet h3 { font-size: 16px; margin: 0 0 8px; }
.sheet-desc { font-size: 13px; color: var(--ink-2); line-height: 1.6; margin: 0 0 6px; }
.sheet-price { font-size: 18px; font-weight: 800; color: var(--danger, #e04b2a); margin: 0 0 14px; }
.sheet-actions { display: flex; gap: 10px; }
.btn { flex: 1; border-radius: 10px; font-size: 14px; font-weight: 700; padding: 12px 0; border: 0; }
.btn.ghost { background: var(--bg); color: var(--ink-2); }
.btn.primary { background: var(--brand); color: #fff; }
.btn.primary:disabled { opacity: .6; }

.toast { position: fixed; left: 50%; bottom: 40px; transform: translateX(-50%); background: rgba(0,0,0,.78); color: #fff; font-size: 13px; padding: 9px 16px; border-radius: 999px; z-index: 60; max-width: 84%; text-align: center; }

.skeleton { background: var(--brand-soft); border-radius: 8px; }
.skeleton.line { height: 12px; margin: 6px 0; }
</style>
