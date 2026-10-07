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
    </header>

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

    <!-- 底部输入 -->
    <div class="input-bar">
      <button class="mic-btn"><Icon name="mic" /></button>
      <input
        v-model="inputText"
        placeholder="输入你想买的商品..."
        @keyup.enter="send"
        :disabled="sending"
      />
      <button class="send-btn" @click="send" :disabled="sending || !inputText.trim()">
        <Icon name="send" size="sm" />
      </button>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, nextTick, onMounted } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { chatSse } from '@/api/chat';
import { getProductPage } from '@/api/product';
import type { ProductPageItem } from '@/api/product';
import { addCart } from '@/api/cart';
import { useUserStore } from '@/stores/user';
import { showToast, formatPrice } from '@/utils';
import Icon from '@/components/Icon.vue';

interface Message {
  role: 'user' | 'ai';
  content?: string;
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

const chips = ['海边度假装备', '帮我送礼', '200 元以内的吹风机'];

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

async function send() {
  const text = inputText.value.trim();
  if (!text || sending.value) return;
  inputText.value = '';

  messages.value.push({ role: 'user', content: text });
  const aiMsg: Message = { role: 'ai', tokens: [], typing: true, toolHint: '' };
  messages.value.push(aiMsg);
  scrollBottom();

  sending.value = true;
  let pendingKeyword = '';

  try {
    await chatSse(text, sessionId.value, (event, data) => {
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
