<template>
  <div class="page profile-page">
    <!-- 渐变头部 -->
    <header class="profile-head">
      <div class="head-row">
        <div class="avatar">{{ avatarChar }}</div>
        <div class="head-info">
          <p class="nickname">{{ profile?.nickname || '智购用户' }}</p>
          <p class="phone">{{ maskedPhone }}</p>
        </div>
        <button class="edit-btn" @click="toast('资料编辑功能规划中')"><Icon name="edit" size="sm" /></button>
      </div>
      <div class="member-card">
        <div class="member-left">
          <p class="member-title"><Icon name="crown" size="sm" /> 智购 AI 会员</p>
          <p class="member-sub">AI 比价 · 专属折扣 · 优先试用</p>
        </div>
        <button class="member-btn" @click="toast('会员开通功能规划中')">开通</button>
      </div>
    </header>

    <!-- 订单栏 -->
    <section class="order-card card" @click="router.push('/orders')">
      <div class="order-head">
        <h3 class="sec-title">我的订单</h3>
        <span class="more">全部订单 <Icon name="chev" size="xs" /></span>
      </div>
      <div class="order-stats">
        <button v-for="s in orderStats" :key="s.label" @click.stop="goOrders(s.status)">
          <Icon :name="s.icon" size="lg" />
          <span>{{ s.label }}</span>
        </button>
      </div>
    </section>

    <!-- AI 购物模型卡 -->
    <section class="ai-model card">
      <div class="ai-model-head">
        <span class="ai-badge"><Icon name="ai" size="xs" /> AI 购物模型</span>
        <span class="ai-version">V2.1</span>
      </div>
      <div class="am-tags">
        <span v-for="t in aiTags" :key="t">{{ t }}</span>
      </div>
      <p class="ai-model-desc">基于你的浏览与购买行为，小智已学习 128 条偏好信号</p>
    </section>

    <!-- 菜单 -->
    <section class="menu card">
      <button v-for="m in menus" :key="m.label" class="menu-item" @click="m.action">
        <span class="menu-ic" :style="{ background: m.bg, color: m.color }"><Icon :name="m.icon" size="sm" /></span>
        <span class="menu-label">{{ m.label }}</span>
        <span v-if="m.hint" class="menu-hint">{{ m.hint }}</span>
        <Icon name="chev" size="xs" />
      </button>
    </section>

    <!-- 退出登录 -->
    <button v-if="userStore.isLoggedIn" class="logout" @click="logout">退出登录</button>

    <!-- AI 提醒中心面板 -->
    <div v-if="reminderOpen" class="reminder-panel card">
      <div class="rp-head">
        <h3 class="sec-title">AI 提醒中心</h3>
        <button class="rp-scan" @click="doScan">立即扫描</button>
        <button class="rp-close" @click="reminderOpen = false">×</button>
      </div>
      <p v-if="reminderLoading" class="rp-empty">加载中…</p>
      <div v-else-if="reminders.length === 0" class="rp-empty">
        暂无待办提醒。小智会主动提醒降价、补货与换季穿搭。
      </div>
      <div v-else class="rp-list">
        <div v-for="r in reminders" :key="r.id" class="rp-item">
          <span class="rp-dot" :class="r.type"></span>
          <div class="rp-body">
            <p class="rp-title">{{ r.title }}</p>
            <p class="rp-detail">{{ r.detail }}</p>
            <p class="rp-time">{{ r.createdAt.slice(0, 16).replace('T', ' ') }}</p>
          </div>
          <button class="rp-done" @click="doRead(r.id)">已读</button>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue';
import { useRouter } from 'vue-router';
import { getProfile } from '@/api/user';
import type { UserProfile } from '@/api/user';
import { listReminders, scanReminders, markReminderRead } from '@/api/reminders';
import type { ReminderItem } from '@/api/reminders';
import { useUserStore } from '@/stores/user';
import { showToast } from '@/utils';
import Icon from '@/components/Icon.vue';

const router = useRouter();
const userStore = useUserStore();
const profile = ref<UserProfile | null>(null);

const orderStats = [
  { icon: 'wallet', label: '待付款', status: 'INIT' },
  { icon: 'truck', label: '待发货', status: 'PAID' },
  { icon: 'layers', label: '待收货', status: 'SHIPPED' },
  { icon: 'refresh', label: '售后', status: 'AFTERSALE' },
];

const aiTags = ['通勤穿搭', '数码性价比', '礼物场景', '百元好物'];

const unreadCount = ref(0);
const reminderHint = computed(() => (unreadCount.value > 0 ? `${unreadCount.value} 条待办` : ''));

const menus = [
  { icon: 'wallet', label: '省钱助手', hint: '比价 · 凑单 · 降价', bg: '#FFF0E8', color: '#FF2D55', action: () => router.push('/cost-saver') },
  { icon: 'bell', label: 'AI 提醒中心', hint: reminderHint, bg: '#FFF0E8', color: '#FF5C39', action: openReminders },
  { icon: 'wallet', label: '我的钱包', hint: '', bg: '#FFF3DF', color: '#FFA62B', action: () => router.push('/wallet') },
  { icon: 'heart', label: '我的收藏', hint: '', bg: '#FFEDF2', color: '#FF4D6D', action: () => router.push('/favorites') },
  { icon: 'eye', label: '浏览历史', hint: '', bg: '#EAF5FF', color: '#2F80ED', action: () => router.push('/history') },
  { icon: 'loc', label: '收货地址', hint: '', bg: '#E9EBFF', color: '#4C5CFF', action: () => router.push('/address') },
  { icon: 'tag', label: '优惠券', hint: '', bg: '#FFEDE7', color: '#FF5C39', action: () => router.push('/coupons') },
  { icon: 'lock', label: '隐私设置', hint: '', bg: '#E3F7F0', color: '#00A87E', action: () => toast('隐私设置规划中') },
  { icon: 'head', label: '帮助与反馈', hint: '', bg: '#FFF3DF', color: '#FFA62B', action: () => toast('帮助中心规划中') },
];

const avatarChar = computed(() => (profile.value?.nickname ? profile.value.nickname.slice(0, 1) : '智'));

const maskedPhone = computed(() => {
  const phone = profile.value?.phone;
  if (!phone || phone.length < 7) return '登录后查看';
  return `${phone.slice(0, 3)}****${phone.slice(-4)}`;
});

function goOrders(status: string) {
  router.push({ path: '/orders', query: { status } });
}

function logout() {
  userStore.clearToken();
  router.push('/login');
}

function toast(msg: string) {
  showToast(msg);
}

const reminderOpen = ref(false);
const reminderLoading = ref(false);
const reminders = ref<ReminderItem[]>([]);

async function loadReminders() {
  if (!userStore.isLoggedIn) return;
  reminderLoading.value = true;
  try {
    reminders.value = await listReminders(true);
    unreadCount.value = reminders.value.length;
  } catch {
    reminders.value = [];
  } finally {
    reminderLoading.value = false;
  }
}

function openReminders() {
  reminderOpen.value = true;
  loadReminders();
}

async function doScan() {
  if (!userStore.isLoggedIn) return;
  reminderLoading.value = true;
  try {
    await scanReminders();
    reminders.value = await listReminders(true);
    unreadCount.value = reminders.value.length;
    if (reminders.value.length === 0) showToast('暂无新提醒');
  } catch {
    /* 忽略 */
  } finally {
    reminderLoading.value = false;
  }
}

async function doRead(id: string) {
  await markReminderRead(id);
  reminders.value = reminders.value.filter((r) => r.id !== id);
  unreadCount.value = reminders.value.length;
}

onMounted(async () => {
  if (!userStore.isLoggedIn) return;
  try {
    profile.value = await getProfile();
  } catch {
    /* 忽略 */
  }
  loadReminders();
});
</script>

<style scoped>
.profile-page { min-height: 100vh; padding-bottom: 80px; }
.profile-head {
  background: linear-gradient(135deg, #4C5CFF, #7A6BFF 60%, #9A7BFF);
  border-radius: 0 0 24px 24px;
  padding: 20px 16px 24px;
  color: #fff;
}
.head-row { display: flex; align-items: center; gap: 12px; }
.avatar {
  width: 54px;
  height: 54px;
  border-radius: 50%;
  background: rgba(255, 255, 255, 0.25);
  border: 2px solid rgba(255, 255, 255, 0.5);
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 22px;
  font-weight: 800;
  flex: none;
}
.head-info { flex: 1; }
.nickname { font-size: 18px; font-weight: 800; }
.phone { font-size: 11.5px; opacity: 0.85; margin-top: 3px; }
.edit-btn { width: 32px; height: 32px; border-radius: 50%; background: rgba(255,255,255,.2); display: flex; align-items: center; justify-content: center; }
.member-card {
  margin-top: 16px;
  background: rgba(255, 255, 255, 0.16);
  border: 1px solid rgba(255, 255, 255, 0.3);
  border-radius: 14px;
  padding: 12px 14px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  backdrop-filter: blur(6px);
}
.member-title { font-size: 13.5px; font-weight: 700; display: flex; align-items: center; gap: 5px; }
.member-sub { font-size: 10.5px; opacity: 0.85; margin-top: 3px; }
.member-btn { background: #fff; color: var(--brand); font-size: 12px; font-weight: 700; border-radius: 999px; padding: 7px 18px; }
.card { background: var(--card); border-radius: var(--radius); padding: 14px 16px; margin: 12px 14px 0; }
.sec-title { font-size: 14px; font-weight: 700; }
.order-head { display: flex; align-items: center; justify-content: space-between; cursor: pointer; }
.more { font-size: 11px; color: var(--ink-3); display: inline-flex; align-items: center; gap: 2px; }
.order-stats { display: flex; margin-top: 14px; }
.order-stats button { flex: 1; display: flex; flex-direction: column; align-items: center; gap: 6px; font-size: 11px; color: var(--ink-2); }
.order-stats svg { color: var(--ink); }
.ai-model-head { display: flex; align-items: center; justify-content: space-between; }
.ai-badge { display: inline-flex; align-items: center; gap: 5px; font-size: 12.5px; font-weight: 700; color: var(--brand); }
.ai-badge svg { color: var(--brand); }
.ai-version { font-size: 10.5px; color: var(--ink-3); }
.am-tags { display: flex; flex-wrap: wrap; gap: 7px; margin-top: 12px; }
.am-tags span { font-size: 11px; color: var(--brand); background: var(--brand-soft); border-radius: 999px; padding: 5px 11px; font-weight: 600; }
.ai-model-desc { font-size: 11px; color: var(--ink-3); margin-top: 11px; }
.menu { display: flex; flex-direction: column; padding: 6px 16px; }
.menu-item { display: flex; align-items: center; gap: 11px; padding: 12px 0; border-bottom: 1px solid var(--line); font-size: 13.5px; color: var(--ink); }
.menu-item:last-child { border-bottom: none; }
.menu-ic { width: 30px; height: 30px; border-radius: 9px; display: flex; align-items: center; justify-content: center; flex: none; }
.menu-label { flex: 1; text-align: left; }
.menu-hint { font-size: 11px; color: var(--ink-3); }
.menu-item > svg { color: var(--ink-3); }
.logout { display: block; margin: 20px auto 0; font-size: 13px; color: var(--danger); background: var(--card); border-radius: 999px; padding: 11px 0; width: calc(100% - 28px); max-width: 386px; }
</style>
<style scoped>
.reminder-panel { position: relative; }
.rp-head { display: flex; align-items: center; gap: 8px; }
.rp-head .sec-title { flex: 1; }
.rp-scan { background: var(--brand); color: #fff; border-radius: 999px; font-size: 11.5px; font-weight: 700; padding: 5px 12px; }
.rp-close { width: 26px; height: 26px; border-radius: 50%; background: #f1f2f4; color: #666; font-size: 14px; line-height: 1; }
.rp-empty { color: var(--ink-3); font-size: 12.5px; line-height: 1.7; padding: 14px 0 6px; }
.rp-list { margin-top: 10px; display: flex; flex-direction: column; gap: 10px; }
.rp-item { display: flex; gap: 10px; align-items: flex-start; background: #fafafb; border-radius: 12px; padding: 10px 12px; }
.rp-dot { width: 8px; height: 8px; border-radius: 50%; margin-top: 5px; flex: none; }
.rp-dot.price_drop { background: #FF5C39; }
.rp-dot.repurchase { background: #FFA62B; }
.rp-dot.season_outfit { background: #2F80ED; }
.rp-body { flex: 1; min-width: 0; }
.rp-title { font-size: 13px; font-weight: 700; }
.rp-detail { font-size: 11.5px; color: var(--ink-2); line-height: 1.6; margin-top: 3px; word-break: break-all; }
.rp-time { font-size: 10.5px; color: var(--ink-3); margin-top: 4px; }
.rp-done { background: #eef0f2; color: var(--ink-2); font-size: 11px; border-radius: 999px; padding: 4px 10px; flex: none; }
</style>
