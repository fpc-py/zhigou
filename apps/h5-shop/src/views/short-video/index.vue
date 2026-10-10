<template>
  <div class="sv-page">
    <header class="sv-head">
      <h1>短视频</h1>
      <span class="sv-sub">图文种草 · 竖屏浏览</span>
    </header>

    <div class="sv-feed" v-if="videos.length">
      <div
        v-for="(v, i) in videos"
        :key="v.id"
        class="sv-card"
        :class="{ active: i === current }"
        @click="onCardTap(i, $event)"
      >
        <img :src="v.videoUrl" class="sv-media" :alt="v.title" />
        <div class="sv-overlay">
          <p class="sv-title">{{ v.title }}</p>
          <p class="sv-content" v-if="v.content">{{ v.content }}</p>
          <p class="sv-meta">
            <span class="sv-author">{{ v.authorName }}</span>
            <span class="sv-views">{{ v.viewCount }} 次浏览</span>
          </p>
        </div>

        <!-- 右侧操作栏 -->
        <div class="sv-actions" @click.stop>
          <button class="act" @click="doLike(v)">
            <Icon name="like" :class="{ on: v.liked }" />
            <b>{{ v.likeCount }}</b>
          </button>
          <button class="act" @click="doFav(v)">
            <Icon name="star" :class="{ on: v.favorited }" />
            <b>{{ v.favoriteCount }}</b>
          </button>
          <button class="act" @click="openComment(v)">
            <Icon name="chat" />
            <b>{{ v.commentCount }}</b>
          </button>
        </div>

        <!-- 底部商品卡 -->
        <div class="sv-goods" v-if="v.spuId" @click.stop="router.push(`/product/${v.spuId}`)">
          <span class="g-tag">种草商品</span>
          <span class="g-text">去逛逛 →</span>
        </div>
      </div>
    </div>

    <div class="sv-empty" v-else>
      <p>暂时没有短视频内容</p>
      <button @click="load">重新加载</button>
    </div>

    <!-- 评论弹层 -->
    <div class="cm-mask" v-if="commentTarget" @click.self="commentTarget = null">
      <div class="cm-panel">
        <h4>评论（{{ commentTarget.commentCount }}）</h4>
        <div class="cm-input-row">
          <input v-model="commentText" placeholder="说点什么…" @keyup.enter="submitComment" />
          <button @click="submitComment">发送</button>
        </div>
      </div>
    </div>
  </div>
</template>

<script lang="ts" setup>
import { ref, onMounted } from 'vue';
import { useRouter } from 'vue-router';
import {
  getVideos,
  toggleVideoLike,
  toggleVideoFavorite,
  postComment,
  type CommunityVideo,
} from '@/api/community';

const router = useRouter();
const videos = ref<CommunityVideo[]>([]);
const current = ref(0);
const commentTarget = ref<CommunityVideo | null>(null);
const commentText = ref('');

async function load() {
  videos.value = await getVideos(1, 20);
}

async function doLike(v: CommunityVideo) {
  const r = await toggleVideoLike(v.id);
  if (r) {
    v.liked = r.liked;
    v.likeCount = r.likeCount;
  }
}

async function doFav(v: CommunityVideo) {
  const r = await toggleVideoFavorite(v.id);
  if (r) {
    v.favorited = r.favorited;
    v.favoriteCount = r.favoriteCount;
  }
}

function openComment(v: CommunityVideo) {
  commentTarget.value = v;
  commentText.value = '';
}

async function submitComment() {
  if (!commentTarget.value || !commentText.value.trim()) return;
  const ok = await postComment(commentTarget.value.id, commentText.value.trim());
  if (ok) {
    commentTarget.value.commentCount += 1;
    commentText.value = '';
  }
}

function onCardTap(i: number, e: Event) {
  // 点击非操作区切换上下卡片
  if ((e.target as HTMLElement).closest('.sv-actions, .sv-goods')) return;
  current.value = i;
}

onMounted(load);
</script>

<style scoped>
.sv-page { min-height: 100vh; background: #0e0f14; color: #fff; }
.sv-head { padding: 12px 16px 8px; display: flex; align-items: baseline; gap: 10px; }
.sv-head h1 { font-size: 18px; font-weight: 700; margin: 0; }
.sv-sub { font-size: 11px; color: rgba(255,255,255,.55); }
.sv-feed {
  height: calc(100vh - 52px);
  overflow-y: scroll;
  scroll-snap-type: y mandatory;
  scrollbar-width: none;
}
.sv-card {
  position: relative;
  height: 100%;
  scroll-snap-align: start;
  overflow: hidden;
  border-radius: 4px;
}
.sv-media { width: 100%; height: 100%; object-fit: cover; display: block; }
.sv-overlay {
  position: absolute; left: 0; right: 0; bottom: 0;
  padding: 40px 72px 12px 16px;
  background: linear-gradient(transparent, rgba(0,0,0,.72));
}
.sv-title { font-size: 16px; font-weight: 700; margin: 0 0 4px; }
.sv-content { font-size: 13px; color: rgba(255,255,255,.92); margin: 0 0 6px; line-height: 1.5; }
.sv-meta { display: flex; align-items: center; gap: 8px; font-size: 12px; color: rgba(255,255,255,.8); }
.sv-actions {
  position: absolute; right: 10px; bottom: 100px;
  display: flex; flex-direction: column; gap: 16px; align-items: center;
}
.act { display: flex; flex-direction: column; align-items: center; gap: 2px; background: none; border: 0; color: #fff; font-size: 11px; }
.act b { font-weight: 600; }
.act .on { color: #ff4d6a; }
.sv-goods {
  position: absolute; left: 12px; bottom: 12px;
  display: flex; align-items: center; gap: 8px;
  background: rgba(255,255,255,.92); color: #111;
  border-radius: 999px; padding: 6px 12px; font-size: 12px; font-weight: 600;
}
.g-tag { color: #ff4d6a; }
.sv-empty { text-align: center; padding-top: 40vh; color: rgba(255,255,255,.6); }
.sv-empty button { margin-top: 12px; padding: 8px 20px; border-radius: 999px; border: 0; background: #2e7dff; color: #fff; }
.cm-mask { position: fixed; inset: 0; background: rgba(0,0,0,.5); display: flex; align-items: flex-end; z-index: 20; }
.cm-panel { width: 100%; background: #fff; color: #111; border-radius: 16px 16px 0 0; padding: 16px; }
.cm-panel h4 { margin: 0 0 12px; font-size: 15px; }
.cm-input-row { display: flex; gap: 8px; }
.cm-input-row input { flex: 1; border: 1px solid #e5e7eb; border-radius: 999px; padding: 10px 14px; font-size: 14px; }
.cm-input-row button { border: 0; background: #2e7dff; color: #fff; border-radius: 999px; padding: 0 18px; font-size: 14px; }
</style>
