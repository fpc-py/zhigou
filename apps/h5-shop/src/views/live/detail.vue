<template>
  <div class="ld-page">
    <div class="ld-stage" :class="'s' + (live?.status ?? 0)">
      <div class="ld-back" @click="router.back()">← 返回</div>
      <img v-if="live?.coverUrl" :src="live.coverUrl" :alt="live?.title" class="ld-cover" />
      <div class="ld-status">
        <span class="ld-badge" :class="'s' + (live?.status ?? 0)">{{ statusText(live?.status ?? 0) }}</span>
        <span v-if="live?.viewCount" class="ld-views">{{ live.viewCount }} 观看</span>
      </div>
      <div class="ld-note" v-if="(live?.status ?? 0) === 1">演示环境：真实直播流接入后在此播放（当前为封面占位）</div>
    </div>

    <div class="ld-info" v-if="live">
      <h2>{{ live.title }}</h2>
      <p class="ld-host">主播：{{ live.authorName }} · {{ (live.likeCount ?? 0) }} 点赞</p>
      <p class="ld-time">{{ timeText(live) }}</p>
      <div class="ld-goods" v-if="live.spuId" @click="router.push(`/product/${live.spuId}`)">
        <span>主推商品</span>
        <b>去看看 →</b>
      </div>
    </div>

    <div v-else class="ld-empty">直播不存在</div>
  </div>
</template>

<script lang="ts" setup>
import { ref, onMounted } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { getLiveDetail, type LiveItem } from '@/api/community';

const route = useRoute();
const router = useRouter();
const live = ref<LiveItem | null>(null);

function statusText(s: number) {
  return s === 0 ? '预告' : s === 1 ? '直播中' : '已结束';
}

function timeText(l: LiveItem) {
  if (l.status === 0 && l.scheduledStart) {
    const d = new Date(l.scheduledStart);
    return `开播时间 ${d.getMonth() + 1}月${d.getDate()}日 ${String(d.getHours()).padStart(2, '0')}:${String(d.getMinutes()).padStart(2, '0')}`;
  }
  if (l.status === 1) return '正在直播';
  if (l.endedAt) return `结束于 ${new Date(l.endedAt).toLocaleString('zh-CN')}`;
  return '';
}

onMounted(async () => {
  live.value = await getLiveDetail(String(route.params.id));
});
</script>

<style scoped>
.ld-page { min-height: 100vh; background: #fff; }
.ld-stage { position: relative; aspect-ratio: 16/9; background: #0e0f14; overflow: hidden; }
.ld-stage.s0 img { filter: grayscale(.35); }
.ld-cover { width: 100%; height: 100%; object-fit: cover; }
.ld-back { position: absolute; left: 12px; top: 12px; background: rgba(0,0,0,.5); color: #fff; border-radius: 999px; padding: 6px 14px; font-size: 13px; z-index: 2; }
.ld-status { position: absolute; right: 12px; top: 12px; display: flex; gap: 8px; z-index: 2; }
.ld-badge { font-size: 11px; font-weight: 700; padding: 4px 10px; border-radius: 999px; }
.ld-badge.s0 { background: #f5f5f5; color: #666; }
.ld-badge.s1 { background: #ff4d6a; color: #fff; }
.ld-badge.s2 { background: #e5e7eb; color: #888; }
.ld-views { background: rgba(0,0,0,.55); color: #fff; font-size: 11px; padding: 4px 10px; border-radius: 999px; }
.ld-note { position: absolute; left: 0; right: 0; bottom: 0; background: rgba(0,0,0,.6); color: #ffd166; font-size: 12px; padding: 8px 14px; text-align: center; }
.ld-info { padding: 16px; }
.ld-info h2 { margin: 0 0 6px; font-size: 18px; font-weight: 700; }
.ld-host { margin: 0 0 4px; font-size: 13px; color: var(--ink-3); }
.ld-time { margin: 0 0 14px; font-size: 12px; color: var(--ink-3); opacity: .8; }
.ld-goods { display: flex; justify-content: space-between; align-items: center; background: #fff4f4; border: 1px solid #ffd7dd; color: #e11d48; border-radius: 12px; padding: 12px 14px; font-size: 14px; font-weight: 600; }
.ld-empty { text-align: center; padding-top: 30vh; color: var(--ink-3); }
</style>
