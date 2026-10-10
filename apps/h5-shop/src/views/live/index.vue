<template>
  <div class="live-page">
    <header class="live-head">
      <h1>直播</h1>
      <span class="live-sub">主播推荐 · 边看边买</span>
    </header>

    <div v-if="lives.length" class="live-list">
      <div v-for="l in lives" :key="l.id" class="live-card" @click="router.push(`/live/${l.id}`)">
        <div class="live-cover">
          <img v-if="l.coverUrl" :src="l.coverUrl" :alt="l.title" />
          <span class="live-badge" :class="'s' + l.status">{{ statusText(l.status) }}</span>
          <span class="live-views" v-if="l.viewCount">{{ l.viewCount }} 观看</span>
        </div>
        <div class="live-info">
          <h3>{{ l.title }}</h3>
          <p class="live-host">{{ l.authorName }}</p>
          <p class="live-time">{{ timeText(l) }}</p>
        </div>
      </div>
    </div>
    <div v-else class="live-empty">
      <p>暂无直播场次</p>
      <button @click="load">重新加载</button>
    </div>
  </div>
</template>

<script lang="ts" setup>
import { ref, onMounted } from 'vue';
import { useRouter } from 'vue-router';
import { getLiveList, type LiveItem } from '@/api/community';

const router = useRouter();
const lives = ref<LiveItem[]>([]);

async function load() {
  lives.value = await getLiveList();
}

function statusText(s: number) {
  return s === 0 ? '预告' : s === 1 ? '直播中' : '已结束';
}

function timeText(l: LiveItem) {
  if (l.status === 0 && l.scheduledStart) {
    const d = new Date(l.scheduledStart);
    return `开播时间 ${d.getMonth() + 1}月${d.getDate()}日 ${String(d.getHours()).padStart(2, '0')}:${String(d.getMinutes()).padStart(2, '0')}`;
  }
  if (l.status === 1) return '正在直播';
  if (l.endedAt) return `已结束 ${new Date(l.endedAt).toLocaleString('zh-CN', { month: 'numeric', day: 'numeric', hour: '2-digit', minute: '2-digit' })}`;
  return '';
}

onMounted(load);
</script>

<style scoped>
.live-page { min-height: 100vh; background: #fff; }
.live-head { padding: 14px 16px 8px; display: flex; align-items: baseline; gap: 10px; }
.live-head h1 { font-size: 20px; font-weight: 700; margin: 0; }
.live-sub { font-size: 12px; color: var(--ink-3); }
.live-list { padding: 8px 16px 80px; display: flex; flex-direction: column; gap: 14px; }
.live-card { background: #fff; border-radius: 14px; overflow: hidden; box-shadow: 0 1px 8px rgba(0,0,0,.06); }
.live-cover { position: relative; aspect-ratio: 16/9; background: #eee; }
.live-cover img { width: 100%; height: 100%; object-fit: cover; }
.live-badge { position: absolute; left: 10px; top: 10px; font-size: 11px; font-weight: 700; padding: 4px 10px; border-radius: 999px; }
.live-badge.s0 { background: #f5f5f5; color: #666; }
.live-badge.s1 { background: #ff4d6a; color: #fff; }
.live-badge.s2 { background: #e5e7eb; color: #888; }
.live-views { position: absolute; right: 10px; top: 10px; background: rgba(0,0,0,.55); color: #fff; font-size: 11px; padding: 3px 8px; border-radius: 999px; }
.live-info { padding: 10px 12px 12px; }
.live-info h3 { margin: 0 0 4px; font-size: 15px; font-weight: 600; }
.live-host { margin: 0 0 2px; font-size: 12px; color: var(--ink-3); }
.live-time { margin: 0; font-size: 11px; color: var(--ink-3); opacity: .8; }
.live-empty { text-align: center; padding-top: 35vh; color: var(--ink-3); }
.live-empty button { margin-top: 12px; padding: 8px 20px; border-radius: 999px; border: 0; background: var(--brand); color: #fff; }
</style>
