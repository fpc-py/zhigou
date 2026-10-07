<template>
  <div class="page comm-page">
    <!-- 头部 -->
    <header class="comm-head">
      <div class="tabs">
        <button class="tab" :class="{ on: tab === 'follow' }" @click="tab = 'follow'">关注</button>
        <button class="tab" :class="{ on: tab === 'recommend' }" @click="tab = 'recommend'">推荐</button>
      </div>
      <button class="publish-btn" @click="toast('发布功能规划中')"><Icon name="plus" size="sm" /> 发布</button>
    </header>

    <!-- 帖子流 -->
    <div class="posts">
      <article v-for="(post, i) in posts" :key="i" class="post card">
        <div class="post-head">
          <div class="post-user">
            <span class="post-avatar" :style="{ background: post.bg }">{{ post.user.slice(0, 1) }}</span>
            <div>
              <p class="post-name">{{ post.user }}</p>
              <p class="post-time">{{ post.time }} <span v-if="post.ai" class="ai-note"><Icon name="ai" size="xs" /> AI 内容</span></p>
            </div>
          </div>
          <button class="follow-btn" :class="{ on: post.followed }" @click="post.followed = !post.followed">
            {{ post.followed ? '已关注' : '关注' }}
          </button>
        </div>

        <p class="post-title">{{ post.title }}</p>
        <div class="post-media" :style="{ background: post.bg }">{{ post.emoji }}</div>
        <p class="post-desc">{{ post.desc }}</p>

        <div class="post-tags">
          <span v-for="t in post.tags" :key="t" class="post-tag">#{{ t }}</span>
        </div>

        <div class="post-actions">
          <button @click="toggle(post, 'like')"><Icon name="heart" size="sm" :class="{ active: post.liked }" /> {{ post.likes }}</button>
          <button @click="toast('评论功能规划中')"><Icon name="comm" size="sm" /> {{ post.comments }}</button>
          <button @click="toast('分享功能规划中')"><Icon name="send" size="sm" /> 分享</button>
        </div>
      </article>
    </div>

    <div class="planning-note">社区为原型演示页，UGC 发布与推荐流规划中</div>
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue';
import { showToast } from '@/utils';
import Icon from '@/components/Icon.vue';

const tab = ref('recommend');

const posts = ref([
  {
    user: '小鹿穿搭', time: '2 小时前', ai: true, bg: '#E9EBFF', emoji: '🧥',
    title: '通勤一周不重样｜AI 帮我搭了 5 套',
    desc: '用智购 AI 衣橱规划了一周通勤穿搭，利用率直接拉满～',
    tags: ['通勤穿搭', 'AI衣橱'], likes: 328, comments: 46, liked: false, followed: true,
  },
  {
    user: '数码君', time: '5 小时前', ai: true, bg: '#FFEDE7', emoji: '💻',
    title: '200 元以内吹风机横评，AI 比价省了 60 块',
    desc: '让小智全网比价，同款吹风机最低价直接拿下。',
    tags: ['数码好物', 'AI比价'], likes: 512, comments: 88, liked: false, followed: false,
  },
  {
    user: '阿夏的衣橱', time: '昨天', ai: false, bg: '#E3F7F0', emoji: '👒',
    title: '周末野餐装备清单',
    desc: '防晒、野餐垫、便携音响，一次配齐。',
    tags: ['野餐', '装备'], likes: 156, comments: 21, liked: false, followed: false,
  },
]);

function toggle(post: any, key: 'like') {
  post.liked = !post.liked;
  post.likes += post.liked ? 1 : -1;
}

function toast(msg: string) {
  showToast(msg);
}
</script>

<style scoped>
.comm-page { min-height: 100vh; padding-bottom: 76px; }
.comm-head { display: flex; align-items: center; justify-content: space-between; padding: 12px 14px 8px; }
.tabs { display: flex; gap: 18px; }
.tab { font-size: 15px; font-weight: 600; color: var(--ink-3); padding: 4px 0; border-bottom: 2px solid transparent; }
.tab.on { color: var(--ink); border-bottom-color: var(--brand); }
.publish-btn { display: inline-flex; align-items: center; gap: 4px; font-size: 11.5px; font-weight: 600; color: #fff; background: var(--brand); border-radius: 999px; padding: 7px 13px; }
.posts { padding: 0 14px; }
.card { background: var(--card); border-radius: var(--radius); padding: 14px; margin-bottom: 12px; }
.post-head { display: flex; align-items: center; justify-content: space-between; }
.post-user { display: flex; align-items: center; gap: 9px; }
.post-avatar { width: 36px; height: 36px; border-radius: 50%; display: flex; align-items: center; justify-content: center; color: var(--ink); font-weight: 700; }
.post-name { font-size: 13px; font-weight: 600; }
.post-time { font-size: 10.5px; color: var(--ink-3); margin-top: 1px; display: flex; align-items: center; gap: 4px; }
.ai-note { display: inline-flex; align-items: center; gap: 3px; color: var(--brand); font-weight: 600; }
.ai-note svg { color: var(--brand); }
.follow-btn { font-size: 11px; font-weight: 600; color: var(--brand); background: var(--brand-soft); border-radius: 999px; padding: 5px 12px; }
.follow-btn.on { color: var(--ink-3); background: var(--bg); }
.post-title { font-size: 14.5px; font-weight: 700; margin-top: 11px; line-height: 1.45; }
.post-media { height: 170px; border-radius: 12px; margin-top: 10px; display: flex; align-items: center; justify-content: center; font-size: 52px; }
.post-desc { font-size: 12.5px; color: var(--ink-2); margin-top: 9px; line-height: 1.6; }
.post-tags { display: flex; gap: 8px; margin-top: 8px; }
.post-tag { font-size: 11px; color: var(--brand); font-weight: 600; }
.post-actions { display: flex; gap: 26px; margin-top: 11px; padding-top: 10px; border-top: 1px solid var(--line); }
.post-actions button { display: inline-flex; align-items: center; gap: 5px; font-size: 12px; color: var(--ink-2); }
.post-actions svg.active { color: var(--accent); fill: var(--accent); }
.planning-note { text-align: center; font-size: 10.5px; color: var(--ink-3); padding: 6px 0; }
</style>
