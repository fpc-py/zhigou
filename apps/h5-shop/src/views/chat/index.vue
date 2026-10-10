<template>
  <div class="page chat-page">
    <!-- 头部 -->
    <header class="chat-head">
      <button class="head-back" @click="router.back()"><Icon name="back" /></button>
      <div class="head-avatar"><Icon name="ai" size="sm" /></div>
      <div class="head-title">
        <p class="head-name">小智</p>
        <p class="head-sub">AI 购物助理 · 实时比价</p>
      </div>
      <span class="head-online"><i />在线</span>
      <button class="head-clear" title="清空会话" @click="onClear"><Icon name="trash" size="xs" /></button>
    </header>

    <!-- AI 功能快捷入口 -->
    <div v-if="messages.length === 0" class="quick">
      <button v-for="q in quickActs" :key="q.label" class="quick-card" @click="sendWith(q.prompt)">
        <span class="quick-icon"><Icon :name="q.icon" size="sm" /></span>
        <span class="quick-txt">
          <span class="quick-label">{{ q.label }}</span>
          <span class="quick-desc">{{ q.desc }}</span>
        </span>
      </button>
    </div>

    <!-- 快捷 chips -->
    <div v-if="messages.length === 0" class="chips">
      <button v-for="c in chips" :key="c" class="chip" @click="sendWith(c)">{{ c }}</button>
    </div>

    <!-- 消息列表 -->
    <div class="msg-list" ref="msgList">
      <div v-for="(msg, i) in messages" :key="i" :class="['msg', msg.role]">
        <div v-if="msg.role === 'ai'" class="msg-avatar ai"><Icon name="ai" size="sm" /></div>
        <div class="msg-col">
          <div class="msg-bubble" :class="msg.role">
            <template v-if="msg.role === 'ai' && msg.tokens">
              <span v-for="(t, ti) in msg.tokens" :key="ti">{{ t }}</span>
              <span v-if="msg.typing" class="typing-cursor">▌</span>
            </template>
            <template v-else>{{ msg.content }}</template>
          </div>

          <!-- 工具调用中的理由框 -->
          <div v-if="msg.role === 'ai' && msg.toolHint" class="ai-reason">
            <Icon name="flash" size="xs" />
            {{ msg.toolHint }}
          </div>

          <!-- 推荐商品 mini-list -->
          <div v-if="msg.products && msg.products.length" class="mini-list">
            <div v-for="p in msg.products" :key="p.spuId" class="mini-item" @click="goProduct(p.spuId)">
              <img v-if="p.mainImage" :src="p.mainImage" :alt="p.name" class="mini-img" />
              <div v-else class="mini-img ph">🛍️</div>
              <div class="mini-info">
                <p class="mini-name">{{ p.name }}</p>
                <p class="mini-price"><b>¥{{ formatPrice(p.priceMin) }}</b></p>
              </div>
              <button class="mini-add" @click.stop="onMiniAdd(p)"><Icon name="plus" /></button>
            </div>
          </div>
        </div>
      </div>
    </div>

    <!-- 待发送图片预览 -->
    <div v-if="pendingImg" class="pending-img">
      <img :src="pendingImg" alt="待发送图片" />
      <button class="pending-del" @click="pendingImg = ''">×</button>
    </div>

    <!-- 底部输入 -->
    <div class="input-bar">
      <button class="mic-btn" title="上传商品图片搜款" @click="pickImage"><Icon name="cam" /></button>
      <input
        v-model="inputText"
        placeholder="输入你想买的商品..."
        @keyup.enter="send"
        :disabled="sending"
      />
      <button class="send-btn" @click="send" :disabled="sending || (!inputText.trim() && !pendingImg)">
        <Icon name="send" size="sm" />
      </button>
      <input ref="fileInput" type="file" accept="image/*" hidden @change="onPickImage" />
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, nextTick, onMounted } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { chatSse, clearChatSession } from '@/api/chat';
import { getProductPage } from '@/api/product';
import type { ProductPageItem } from '@/api/product';
import { uploadImage } from '@/api/file';
import { addCart } from '@/api/cart';
import { useUserStore } from '@/stores/user';
import { showToast, formatPrice } from '@/utils';
import Icon from '@/components/Icon.vue';

interface Message {
  role: 'user' | 'ai';
  content?: string;
  image?: string;
  tokens?: string[];
  typing?: boolean;
  toolHint?: string;
  products?: ProductPageItem[];
}

const route = useRoute();
const router = useRouter();
const inputText = ref('');
const sending = ref(false);
const messages = ref<Message[]>([]);
const msgList = ref<HTMLElement | null>(null);
const sessionId = ref(Math.random().toString(36).slice(2, 10));
const fileInput = ref<HTMLInputElement | null>(null);
const pendingImg = ref('');

const chips = ['海边度假装备', '帮我送礼', '200 元以内的吹风机'];

/** AI 功能快捷入口（对齐 P1/P2 AI 工具：送礼/比价/售后/物流/补货） */
const quickActs = [
  { icon: 'gift', label: 'AI 送礼', desc: '生日/纪念日方案', prompt: '送女朋友生日礼物，预算 500 左右，帮我出个送礼方案' },
  { icon: 'tag', label: '跨平台比价', desc: '全网最低价', prompt: '帮我跨平台比价 SKU 9000000000000000022 这款黑色蓝牙耳机，哪个平台最划算' },
  { icon: 'shield', label: '售后助手', desc: '话术/进度', prompt: '我的商品有问题，帮我生成售后申请话术' },
  { icon: 'truck', label: '物流管家', desc: '轨迹/预警', prompt: '帮我查一下我的快递物流到哪里了' },
  { icon: 'refresh', label: '补货提醒', desc: '消耗品周期', prompt: '帮我看看我该补点什么了' },
];

/** 打开图片选择（图片搜款入口） */
function pickImage() {
  if (sending.value) return;
  fileInput.value?.click();
}

/** 选择后上传 file-service，拿 URL 待发送 */
async function onPickImage(e: Event) {
  const input = e.target as HTMLInputElement;
  const file = input.files?.[0];
  input.value = '';
  if (!file) return;
  if (!/^image\//.test(file.type)) {
    showToast('请选择图片文件');
    return;
  }
  if (!useUserStore().isLoggedIn) {
    router.push('/login');
    return;
  }
  try {
    const resp = await uploadImage(file);
    pendingImg.value = resp.url;
    showToast('图片已就绪，点击发送即可搜款');
  } catch {
    showToast('图片上传失败，请重试');
  }
}

function scrollBottom() {
  nextTick(() => {
    if (msgList.value) msgList.value.scrollTop = msgList.value.scrollHeight;
  });
}

function goProduct(spuId: string) {
  router.push(`/product/${spuId}`);
}

async function onMiniAdd(p: ProductPageItem) {
  const skuId = p.skus?.[0]?.skuId;
  if (!skuId) {
    showToast('该商品暂无可售规格');
    return;
  }
  if (!useUserStore().isLoggedIn) {
    router.push('/login');
    return;
  }
  try {
    await addCart(skuId);
    showToast('已加入购物车');
  } catch {
    /* 已提示 */
  }
}

function sendWith(text: string) {
  inputText.value = text;
  send();
}

async function onClear() {
  if (sending.value) {
    showToast('回复生成中，请稍后再清空');
    return;
  }
  const ok = await clearChatSession(sessionId.value);
  if (ok) {
    messages.value = [];
    showToast('已清空会话记忆');
  } else {
    showToast('清空失败，请重试');
  }
}

async function send() {
  const text = inputText.value.trim();
  if ((!text && !pendingImg.value) || sending.value) return;
  const imgUrl = pendingImg.value;
  inputText.value = '';
  pendingImg.value = '';

  messages.value.push({ role: 'user', content: text || '帮我看下这张图的商品', image: imgUrl || undefined });
  const aiMsg: Message = { role: 'ai', tokens: [], typing: true, toolHint: '' };
  messages.value.push(aiMsg);
  scrollBottom();

  sending.value = true;
  let pendingKeyword = '';

  try {
    await chatSse(text, sessionId.value, imgUrl, (event, data) => {
      if (event === 'token') {
        aiMsg.tokens = aiMsg.tokens || [];
        aiMsg.tokens.push(data.content);
        aiMsg.toolHint = '';
      } else if (event === 'tool_call') {
        pendingKeyword = data.args?.keyword || '';
        aiMsg.toolHint = pendingKeyword
          ? `小智正在全网检索「${pendingKeyword}」…`
          : '小智正在为您查询…';
      } else if (event === 'tool_result') {
        aiMsg.toolHint = '';
        // 用关键词拉真实商品渲染 mini-list（失败则静默降级）
        if (pendingKeyword) {
          getProductPage({ keyword: pendingKeyword, pageSize: 3 })
            .then((res) => {
              if (res.records?.length) aiMsg.products = res.records.slice(0, 3);
              scrollBottom();
            })
            .catch(() => {});
        }
      } else if (event === 'error') {
        aiMsg.tokens = aiMsg.tokens || [];
        aiMsg.tokens.push(data.message || 'AI 助手暂时不可用');
      } else if (event === 'done') {
        aiMsg.typing = false;
        aiMsg.content = (aiMsg.tokens || []).join('');
        aiMsg.toolHint = '';
      }
      scrollBottom();
    });
  } catch {
    aiMsg.tokens = ['发送失败，请稍后重试'];
    aiMsg.typing = false;
  } finally {
    sending.value = false;
  }
}

onMounted(() => {
  const q = route.query.q;
  if (typeof q === 'string' && q.trim()) {
    sendWith(q);
  }
});
</script>

<style scoped>
.chat-page {
  display: flex;
  flex-direction: column;
  height: 100vh;
  background: var(--bg);
}
.chat-head {
  flex: none;
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 12px 14px;
  background: var(--card);
  border-bottom: 1px solid var(--line);
}
.head-back { display: flex; align-items: center; color: var(--ink); padding: 4px; }
.head-avatar {
  width: 34px;
  height: 34px;
  border-radius: 50%;
  background: linear-gradient(135deg, var(--brand), #8A94FF);
  color: #fff;
  display: flex;
  align-items: center;
  justify-content: center;
  flex: none;
}
.head-title { flex: 1; }
.head-name { font-size: 15px; font-weight: 700; line-height: 1.3; }
.head-sub { font-size: 10.5px; color: var(--ink-3); }
.head-online {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  font-size: 10.5px;
  color: var(--mint);
  font-weight: 600;
}
.head-online i { width: 6px; height: 6px; border-radius: 50%; background: var(--mint); }
.head-clear {
  flex: none;
  color: var(--ink-3);
  padding: 6px;
  display: flex;
  align-items: center;
  justify-content: center;
}
.chips {
  flex: none;
  display: flex;
  gap: 8px;
  padding: 12px 14px 2px;
  overflow-x: auto;
}
.chip {
  flex: none;
  font-size: 12px;
  font-weight: 600;
  color: var(--brand);
  background: var(--brand-soft);
  border-radius: 999px;
  padding: 7px 13px;
}
.quick {
  flex: none;
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 8px;
  padding: 12px 14px 2px;
}
.quick-card {
  display: flex;
  align-items: center;
  gap: 9px;
  background: var(--card);
  border: 1px solid var(--line);
  border-radius: 12px;
  padding: 10px 11px;
  text-align: left;
}
.quick-icon {
  width: 32px;
  height: 32px;
  border-radius: 9px;
  background: var(--brand-soft);
  color: var(--brand);
  display: flex;
  align-items: center;
  justify-content: center;
  flex: none;
}
.quick-txt { display: flex; flex-direction: column; min-width: 0; }
.quick-label { font-size: 12.5px; font-weight: 700; color: var(--ink); }
.quick-desc { font-size: 10px; color: var(--ink-3); margin-top: 1px; }
.msg-list {
  flex: 1;
  overflow-y: auto;
  padding: 14px;
}
.msg {
  display: flex;
  gap: 8px;
  margin-bottom: 14px;
}
.msg.user { flex-direction: row-reverse; }
.msg-avatar {
  width: 30px;
  height: 30px;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  flex: none;
  background: var(--line);
  color: var(--ink-3);
}
.msg-avatar.ai {
  background: linear-gradient(135deg, var(--brand), #8A94FF);
  color: #fff;
}
.msg-col { max-width: 78%; display: flex; flex-direction: column; gap: 6px; }
.msg.user .msg-col { align-items: flex-end; }
.msg-bubble {
  padding: 10px 13px;
  border-radius: 14px;
  font-size: 13.5px;
  line-height: 1.6;
  word-break: break-word;
}
.msg-bubble.user {
  background: var(--brand);
  color: #fff;
  border-bottom-right-radius: 4px;
}
.msg-bubble.ai {
  background: var(--card);
  border: 1px solid var(--line);
  border-bottom-left-radius: 4px;
}
.typing-cursor { animation: blink 0.8s infinite; }
@keyframes blink { 50% { opacity: 0; } }
.ai-reason {
  display: flex;
  align-items: flex-start;
  gap: 5px;
  font-size: 11.5px;
  color: var(--brand);
  background: var(--brand-soft);
  border-radius: 10px;
  padding: 8px 10px;
  line-height: 1.5;
}
.ai-reason svg { margin-top: 2px; flex: none; }
.mini-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
  margin-top: 2px;
}
.mini-item {
  display: flex;
  align-items: center;
  gap: 9px;
  background: var(--card);
  border: 1px solid var(--line);
  border-radius: 12px;
  padding: 8px;
  cursor: pointer;
}
.mini-img {
  width: 46px;
  height: 46px;
  border-radius: 9px;
  object-fit: cover;
  background: linear-gradient(135deg, #f6f7fb, #eef0f7);
  flex: none;
}
.mini-img.ph { display: flex; align-items: center; justify-content: center; font-size: 20px; }
.mini-info { flex: 1; min-width: 0; }
.mini-name {
  font-size: 12px;
  font-weight: 500;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.mini-price { color: var(--accent); font-size: 11px; font-weight: 600; margin-top: 2px; }
.mini-price b { font-size: 14px; }
.mini-add {
  width: 24px;
  height: 24px;
  border-radius: 50%;
  background: var(--brand);
  color: #fff;
  display: flex;
  align-items: center;
  justify-content: center;
  flex: none;
}
.mini-add svg { width: 13px; height: 13px; }
.input-bar {
  flex: none;
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 12px 14px calc(12px + env(safe-area-inset-bottom, 0px));
  border-top: 1px solid var(--line);
  background: var(--card);
}
.mic-btn { color: var(--ink-3); display: flex; padding: 6px; }
.pending-img {
  flex: none;
  position: relative;
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 14px 0;
}
.pending-img img {
  width: 52px;
  height: 52px;
  border-radius: 10px;
  object-fit: cover;
  border: 1px solid var(--brand);
}
.pending-del {
  position: absolute;
  top: 4px;
  left: 56px;
  width: 18px;
  height: 18px;
  border-radius: 50%;
  background: rgba(0, 0, 0, 0.55);
  color: #fff;
  font-size: 12px;
  line-height: 1;
  display: flex;
  align-items: center;
  justify-content: center;
}
.input-bar input {
  flex: 1;
  height: 38px;
  padding: 0 14px;
  background: var(--bg);
  border-radius: 999px;
  font-size: 13.5px;
}
.send-btn {
  width: 38px;
  height: 38px;
  border-radius: 50%;
  background: var(--brand);
  color: #fff;
  display: flex;
  align-items: center;
  justify-content: center;
  flex: none;
}
.send-btn:disabled { opacity: 0.45; }
</style>
