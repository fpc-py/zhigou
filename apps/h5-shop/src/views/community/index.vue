<template>
  <div class="page comm-page">
    <!-- 头部 -->
    <header class="comm-head">
      <div class="tabs">
        <button class="tab" :class="{ on: tab === 'recommend' }" @click="switchTab('recommend')">推荐</button>
        <button class="tab" :class="{ on: tab === 'mine' }" @click="switchTab('mine')">我的</button>
      </div>
      <button class="publish-btn" @click="openPublish"><Icon name="plus" size="sm" /> 发布</button>
    </header>

    <!-- 信息流 -->
    <main class="body">
      <div v-if="loading" class="feed">
        <div v-for="i in 3" :key="i" class="note-card">
          <div class="skeleton line" style="width: 60%" />
          <div class="skeleton line" style="width: 90%" />
          <div class="skeleton line" style="width: 75%" />
        </div>
      </div>

      <EmptyState v-else-if="!notes.length" icon="comm" text="还没有内容，来发布第一篇种草笔记吧" />

      <template v-else>
        <article v-for="n in notes" :key="n.id" class="note-card" @click="openDetail(n)">
          <div class="note-head">
            <span class="avatar">{{ (n.authorName || '友').slice(0, 1) }}</span>
            <div class="note-user">
              <p class="name">{{ n.authorName }}</p>
              <p class="time">{{ fmtTime(n.createdAt) }}</p>
            </div>
            <span v-if="n.fakeFlag" class="fake-tag">疑似营销</span>
          </div>

          <h3 class="note-title">{{ n.title }}</h3>
          <p class="note-content" v-if="n.content">{{ n.content.slice(0, 120) }}{{ n.content.length > 120 ? '…' : '' }}</p>

          <div v-if="n.spuName" class="note-spu">关联商品 · {{ n.spuName }}</div>

          <div v-if="n.images && n.images.length" class="note-imgs">
            <img v-for="(img, i) in n.images.slice(0, 3)" :key="i" :src="img" alt="" loading="lazy" />
          </div>

          <div class="note-actions" @click.stop>
            <button :class="{ active: n.liked }" @click="like(n)">
              <Icon name="heart" size="sm" /> {{ n.likeCount }}
            </button>
            <button :class="{ active: n.favorited }" @click="fav(n)">
              <Icon name="star" size="sm" /> {{ n.favoriteCount }}
            </button>
            <button @click="openDetail(n)"><Icon name="comm" size="sm" /> {{ n.commentCount }}</button>
          </div>
        </article>
      </template>
    </main>

    <!-- 发布弹层 -->
    <div v-if="publishOpen" class="mask" @click.self="publishOpen = false">
      <div class="sheet publish-sheet">
        <h3 class="sheet-title">发布种草笔记</h3>
        <input v-model="form.title" class="pub-input" placeholder="标题（最多 60 字）" maxlength="60" />
        <textarea v-model="form.content" class="pub-text" placeholder="分享你的真实使用体验…" rows="5" maxlength="5000" />
        <div class="pub-ai">
          <input v-model="form.spuId" class="pub-spu" placeholder="关联商品 SPU ID（可选）" />
          <button class="ai-btn" :disabled="aiLoading" @click="genDraft">
            {{ aiLoading ? '生成中…' : 'AI 写文案' }}
          </button>
        </div>
        <p v-if="aiInfo" class="ai-info">{{ aiInfo }}</p>
        <div class="pub-actions">
          <button class="cancel-btn" @click="publishOpen = false">取消</button>
          <button class="ok-btn" :disabled="submitting" @click="submitPublish">{{ submitting ? '发布中…' : '发布' }}</button>
        </div>
      </div>
    </div>

    <!-- 详情弹层 -->
    <div v-if="detailOpen" class="mask" @click.self="detailOpen = false">
      <div class="sheet detail-sheet">
        <div v-if="detail" class="detail-body">
          <div class="note-head">
            <span class="avatar">{{ (detail.authorName || '友').slice(0, 1) }}</span>
            <div class="note-user">
              <p class="name">{{ detail.authorName }}</p>
              <p class="time">{{ fmtTime(detail.createdAt) }}</p>
            </div>
            <span v-if="detail.fakeFlag" class="fake-tag">疑似营销</span>
          </div>
          <h3 class="note-title">{{ detail.title }}</h3>
          <p class="detail-content">{{ detail.content }}</p>
          <div v-if="detail.spuName" class="note-spu">关联商品 · {{ detail.spuName }}</div>
          <div v-if="detail.images && detail.images.length" class="note-imgs">
            <img v-for="(img, i) in detail.images" :key="i" :src="img" alt="" loading="lazy" />
          </div>

          <div class="comment-sec">
            <h4>评论 {{ detail.commentCount }}</h4>
            <div v-if="!detail.comments || !detail.comments.length" class="no-comment">暂无评论，来抢沙发</div>
            <div v-for="c in detail.comments" :key="c.id" class="comment-row">
              <span class="c-name">{{ c.authorName }}</span>
              <span class="c-content">{{ c.content }}</span>
            </div>
            <div class="comment-input">
              <input v-model="commentText" placeholder="说点什么…" maxlength="500" @keyup.enter="submitComment" />
              <button class="mini-ok" :disabled="commenting" @click="submitComment">发送</button>
            </div>
          </div>
        </div>
      </div>
    </div>

    <div v-if="toast" class="toast">{{ toast }}</div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue';
import Icon from '@/components/Icon.vue';
import EmptyState from '@/components/EmptyState.vue';
import {
  getNotes,
  getNoteDetail,
  getMyNotes,
  publishNote,
  toggleLike,
  toggleFavorite,
  postComment,
  aiWriter,
  type CommunityNote,
} from '@/api/community';

const tab = ref('recommend');
const notes = ref<CommunityNote[]>([]);
const loading = ref(true);
const toast = ref('');

const publishOpen = ref(false);
const form = ref({ title: '', content: '', spuId: '' });
const aiLoading = ref(false);
const aiInfo = ref('');
const submitting = ref(false);

const detailOpen = ref(false);
const detail = ref<CommunityNote | null>(null);
const commentText = ref('');
const commenting = ref(false);

function showToast(msg: string) {
  toast.value = msg;
  setTimeout(() => (toast.value = ''), 1800);
}

function fmtTime(t: string): string {
  if (!t) return '';
  const d = new Date(t);
  const now = new Date();
  const diff = (now.getTime() - d.getTime()) / 1000;
  if (diff < 60) return '刚刚';
  if (diff < 3600) return `${Math.floor(diff / 60)} 分钟前`;
  if (diff < 86400) return `${Math.floor(diff / 3600)} 小时前`;
  return `${d.getMonth() + 1}-${d.getDate()}`;
}

async function load() {
  loading.value = true;
  try {
    const page = await getNotes(1, 10);
    notes.value = page.records;
  } catch {
    notes.value = [];
  } finally {
    loading.value = false;
  }
}

async function switchTab(t: string) {
  tab.value = t;
  loading.value = true;
  try {
    notes.value = t === 'mine' ? await getMyNotes() : (await getNotes(1, 10)).records;
  } catch {
    notes.value = [];
  } finally {
    loading.value = false;
  }
}

// 发布
function openPublish() {
  form.value = { title: '', content: '', spuId: '' };
  aiInfo.value = '';
  publishOpen.value = true;
}

async function genDraft() {
  if (!form.value.spuId) {
    showToast('先填写关联商品 SPU ID');
    return;
  }
  aiLoading.value = true;
  aiInfo.value = '';
  const res = await aiWriter(form.value.spuId, '日常');
  aiLoading.value = false;
  if (res?.draft) {
    form.value.content = res.draft;
    aiInfo.value = `已按真实商品数据生成文案（¥${(res.priceFen / 100).toFixed(2)} · ${res.avgRating} 分 / ${res.reviewTotal} 条评价）`;
  } else {
    aiInfo.value = '生成失败，请稍后再试或手动填写';
  }
}

async function submitPublish() {
  if (!form.value.title.trim() || !form.value.content.trim()) {
    showToast('标题和正文不能为空');
    return;
  }
  submitting.value = true;
  try {
    const res = await publishNote({
      title: form.value.title.trim(),
      content: form.value.content.trim(),
      spuId: form.value.spuId || null,
    });
    if (res) {
      showToast(res.fakeFlag ? '发布成功（已标记疑似营销内容）' : '发布成功');
      publishOpen.value = false;
      await switchTab(tab.value);
    } else {
      showToast('发布失败，请稍后再试');
    }
  } catch (e: any) {
    showToast(e?.response?.data?.message || '发布失败，请稍后再试');
  } finally {
    submitting.value = false;
  }
}

// 互动
async function like(n: CommunityNote) {
  const res = await toggleLike(n.id);
  if (res) {
    n.liked = res.liked;
    n.likeCount = res.likeCount;
    if (detail.value?.id === n.id) {
      detail.value.liked = res.liked;
      detail.value.likeCount = res.likeCount;
    }
  }
}

async function fav(n: CommunityNote) {
  const res = await toggleFavorite(n.id);
  if (res) {
    n.favorited = res.favorited;
    n.favoriteCount = res.favoriteCount;
    if (detail.value?.id === n.id) {
      detail.value.favorited = res.favorited;
      detail.value.favoriteCount = res.favoriteCount;
    }
  }
}

// 详情
async function openDetail(n: CommunityNote) {
  detailOpen.value = true;
  detail.value = null;
  commentText.value = '';
  try {
    detail.value = await getNoteDetail(n.id);
  } catch {
    detail.value = n;
  }
}

async function submitComment() {
  const content = commentText.value.trim();
  if (!content) return;
  commenting.value = true;
  try {
    await postComment(detail.value!.id, content);
    commentText.value = '';
    const d = await getNoteDetail(detail.value!.id);
    if (d) detail.value = d;
    const t = notes.value.find((x) => x.id === detail.value!.id);
    if (t && d) t.commentCount = d.commentCount;
  } catch (e: any) {
    showToast(e?.response?.data?.message || '评论失败');
  } finally {
    commenting.value = false;
  }
}

onMounted(load);
</script>

<style scoped>
.comm-page { background: #f6f7f9; min-height: 100vh; padding-bottom: 70px; }
.comm-head { position: sticky; top: 0; z-index: 20; display: flex; align-items: center; justify-content: space-between;
  padding: 12px 16px; background: #fff; border-bottom: 1px solid #f0f0f0; }
.tabs { display: flex; gap: 4px; }
.tab { padding: 6px 14px; border-radius: 999px; font-size: 14px; color: #666; background: transparent; border: none; }
.tab.on { background: #ff5a36; color: #fff; font-weight: 600; }
.publish-btn { display: flex; align-items: center; gap: 4px; color: #ff5a36; font-size: 13px; background: none; border: none; }
.body { padding: 10px 12px; }
.note-card { background: #fff; border-radius: 12px; padding: 14px; margin-bottom: 10px; box-shadow: 0 1px 4px rgba(0,0,0,.04); }
.note-head { display: flex; align-items: center; gap: 10px; }
.avatar { width: 34px; height: 34px; border-radius: 50%; display: flex; align-items: center; justify-content: center;
  background: linear-gradient(135deg, #ff9a6c, #ff5a36); color: #fff; font-size: 15px; font-weight: 600; flex: none; }
.note-user { flex: 1; }
.name { font-size: 14px; font-weight: 600; color: #222; }
.time { font-size: 11px; color: #999; }
.fake-tag { font-size: 10px; color: #b26a00; background: #fff3d6; border-radius: 4px; padding: 2px 6px; }
.note-title { font-size: 16px; font-weight: 600; color: #222; margin: 10px 0 6px; line-height: 1.4; }
.note-content { font-size: 13px; color: #555; line-height: 1.6; }
.note-spu { display: inline-block; margin-top: 8px; font-size: 11px; color: #ff5a36; background: #fff0ec; border-radius: 6px; padding: 3px 8px; }
.note-imgs { display: flex; gap: 6px; margin-top: 10px; }
.note-imgs img { width: 32%; aspect-ratio: 1; border-radius: 8px; object-fit: cover; }
.note-actions { display: flex; gap: 18px; margin-top: 10px; }
.note-actions button { display: flex; align-items: center; gap: 4px; font-size: 12px; color: #888; background: none; border: none; }
.note-actions button.active { color: #ff5a36; }
.mask { position: fixed; inset: 0; background: rgba(0,0,0,.45); z-index: 50; display: flex; align-items: flex-end; }
.sheet { background: #fff; border-radius: 16px 16px 0 0; padding: 16px; width: 100%; max-height: 82vh; overflow-y: auto; }
.sheet-title { font-size: 16px; font-weight: 600; margin-bottom: 12px; }
.pub-input, .pub-text, .pub-spu { width: 100%; box-sizing: border-box; border: 1px solid #e5e5e5; border-radius: 8px; padding: 10px 12px; font-size: 14px; margin-bottom: 10px; outline: none; }
.pub-text { resize: none; }
.pub-ai { display: flex; gap: 8px; }
.pub-spu { flex: 1; margin-bottom: 0; }
.ai-btn { flex: none; background: #f0f0ff; color: #5a5aff; border: none; border-radius: 8px; padding: 0 12px; font-size: 13px; }
.ai-btn:disabled { opacity: .5; }
.ai-info { font-size: 11px; color: #5a5aff; margin-top: 6px; }
.pub-actions { display: flex; gap: 10px; margin-top: 14px; }
.cancel-btn, .ok-btn { flex: 1; padding: 11px 0; border-radius: 10px; font-size: 14px; border: none; }
.cancel-btn { background: #f2f2f2; color: #666; }
.ok-btn { background: linear-gradient(135deg, #ff9a6c, #ff5a36); color: #fff; font-weight: 600; }
.ok-btn:disabled { opacity: .6; }
.detail-sheet { padding-bottom: 24px; }
.detail-content { font-size: 14px; color: #333; line-height: 1.8; margin: 8px 0; white-space: pre-wrap; }
.comment-sec { margin-top: 16px; border-top: 1px solid #f0f0f0; padding-top: 12px; }
.comment-sec h4 { font-size: 13px; color: #333; margin-bottom: 10px; }
.no-comment { font-size: 12px; color: #aaa; text-align: center; padding: 10px 0; }
.comment-row { display: flex; gap: 8px; font-size: 13px; padding: 7px 0; }
.c-name { color: #5a5aff; flex: none; }
.c-content { color: #333; }
.comment-input { display: flex; gap: 8px; margin-top: 10px; }
.comment-input input { flex: 1; border: 1px solid #e5e5e5; border-radius: 999px; padding: 8px 14px; font-size: 13px; outline: none; }
.mini-ok { background: #ff5a36; color: #fff; border: none; border-radius: 999px; padding: 0 16px; font-size: 13px; }
.mini-ok:disabled { opacity: .6; }
.toast { position: fixed; left: 50%; bottom: 40px; transform: translateX(-50%); background: rgba(0,0,0,.78); color: #fff;
  font-size: 13px; padding: 9px 16px; border-radius: 999px; z-index: 60; max-width: 84%; text-align: center; }
</style>
