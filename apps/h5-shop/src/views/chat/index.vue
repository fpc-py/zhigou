<template>
  <div class="page chat-page">
    <!-- 消息列表 -->
    <div class="msg-list" ref="msgList">
      <div v-if="messages.length === 0" class="empty-chat">
        <EmptyState illustration="🤖" text="问问小智你想买什么～" />
      </div>

      <div v-for="(msg, i) in messages" :key="i" :class="['msg', msg.role]">
        <div class="msg-avatar">{{ msg.role === 'user' ? '👤' : '🤖' }}</div>
        <div class="msg-bubble">
          <template v-if="msg.role === 'ai' && msg.tokens">
            <span v-for="(t, ti) in msg.tokens" :key="ti">{{ t }}</span>
            <span v-if="msg.typing" class="typing-cursor">▌</span>
          </template>
          <template v-else>{{ msg.content }}</template>
        </div>
      </div>

      <!-- 工具调用提示 -->
      <div v-if="toolHint" class="tool-hint">{{ toolHint }}</div>
    </div>

    <!-- 底部输入 -->
    <div class="input-bar">
      <input
        v-model="inputText"
        placeholder="输入你想买的商品..."
        @keyup.enter="send"
        :disabled="sending"
      />
      <button class="send-btn" @click="send" :disabled="sending || !inputText.trim()">
        {{ sending ? '...' : '发送' }}
      </button>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, nextTick } from 'vue';
import { chatSse } from '@/api/chat';
import { useUserStore } from '@/stores/user';

interface Message {
  role: 'user' | 'ai';
  content?: string;
  tokens?: string[];
  typing?: boolean;
}

const inputText = ref('');
const sending = ref(false);
const messages = ref<Message[]>([]);
const toolHint = ref('');
const msgList = ref<HTMLElement | null>(null);
const sessionId = ref(Math.random().toString(36).slice(2, 10));

function scrollBottom() {
  nextTick(() => {
    if (msgList.value) msgList.value.scrollTop = msgList.value.scrollHeight;
  });
}

async function send() {
  const text = inputText.value.trim();
  if (!text || sending.value) return;
  inputText.value = '';

  // 用户消息
  messages.value.push({ role: 'user', content: text });
  const aiMsg: Message = { role: 'ai', tokens: [], typing: true };
  messages.value.push(aiMsg);
  scrollBottom();

  sending.value = true;
  toolHint.value = '';

  try {
    const userStore = useUserStore();
    await chatSse(text, sessionId.value, (event, data) => {
      if (event === 'token') {
        aiMsg.tokens = aiMsg.tokens || [];
        aiMsg.tokens.push(data.content);
        scrollBottom();
      } else if (event === 'tool_call') {
        toolHint.value = `🔍 小智正在查询${data.args?.keyword ? '「' + data.args.keyword + '」' : ''}...`;
      } else if (event === 'tool_result') {
        toolHint.value = '';
      } else if (event === 'error') {
        aiMsg.tokens = aiMsg.tokens || [];
        aiMsg.tokens.push(data.message || 'AI 助手暂时不可用');
      } else if (event === 'done') {
        aiMsg.typing = false;
        // 组装完整 content
        aiMsg.content = (aiMsg.tokens || []).join('');
      }
      scrollBottom();
    });
  } catch (err: any) {
    aiMsg.tokens = ['发送失败，请稍后重试'];
    aiMsg.typing = false;
  } finally {
    sending.value = false;
    toolHint.value = '';
  }
}
</script>

<style scoped>
.chat-page {
  display: flex;
  flex-direction: column;
  height: 100vh;
  padding-bottom: 0;
}
.msg-list {
  flex: 1;
  overflow-y: auto;
  padding: 16px;
}
.empty-chat {
  margin-top: 80px;
}
.msg {
  display: flex;
  gap: 10px;
  margin-bottom: 16px;
}
.msg.user {
  flex-direction: row-reverse;
}
.msg-avatar {
  width: 32px;
  height: 32px;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  background: var(--line);
  font-size: 16px;
}
.msg-bubble {
  max-width: 75%;
  padding: 10px 14px;
  border-radius: 14px;
  font-size: 14px;
  line-height: 1.6;
  word-break: break-word;
}
.msg.user .msg-bubble {
  background: var(--brand);
  color: #fff;
  border-bottom-right-radius: 4px;
}
.msg.ai .msg-bubble {
  background: var(--card);
  border: 1px solid var(--line);
  border-bottom-left-radius: 4px;
}
.typing-cursor {
  animation: blink 0.8s infinite;
}
@keyframes blink {
  50% { opacity: 0; }
}
.tool-hint {
  text-align: center;
  color: var(--ink-3);
  font-size: 12px;
  padding: 4px;
}
.input-bar {
  flex: none;
  display: flex;
  gap: 8px;
  padding: 12px 16px;
  border-top: 1px solid var(--line);
  background: var(--card);
}
.input-bar input {
  flex: 1;
  height: 40px;
  padding: 0 14px;
  background: var(--bg);
  border-radius: 999px;
  font-size: 14px;
}
.send-btn {
  height: 40px;
  padding: 0 20px;
  background: var(--brand);
  color: #fff;
  border-radius: 999px;
  font-size: 14px;
  font-weight: 500;
}
.send-btn:disabled {
  opacity: 0.5;
}
</style>