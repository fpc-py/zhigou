<template>
  <div class="page login-page">
    <div class="login-header">
      <div class="logo-area">🛒</div>
      <h1 class="title">智购</h1>
      <p class="subtitle">AI 超级商城 · 对话式购物体验</p>
    </div>

    <div class="login-form">
      <div class="field">
        <label>手机号</label>
        <input
          v-model="phone"
          type="tel"
          maxlength="11"
          placeholder="请输入手机号"
          class="input"
        />
      </div>

      <div class="field code-field">
        <label>验证码</label>
        <div class="code-row">
          <input
            v-model="code"
            type="text"
            maxlength="6"
            placeholder="请输入验证码"
            class="input"
          />
          <button
            class="code-btn"
            :disabled="sendingCode || cooldown > 0"
            @click="sendCode"
          >
            {{ cooldown > 0 ? `${cooldown}s` : '获取验证码' }}
          </button>
        </div>
      </div>

      <button class="login-btn" :disabled="!canLogin || loggingIn" @click="doLogin">
        {{ loggingIn ? '登录中...' : '登录' }}
      </button>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed } from 'vue';
import { useRouter } from 'vue-router';
import { useUserStore } from '@/stores/user';
import * as authApi from '@/api/auth';

const router = useRouter();
const userStore = useUserStore();

const phone = ref('');
const code = ref('');
const sendingCode = ref(false);
const cooldown = ref(0);
const loggingIn = ref(false);

const canLogin = computed(() => phone.value.length === 11 && code.value.length >= 4);

let timer: ReturnType<typeof setInterval> | null = null;

async function sendCode() {
  if (phone.value.length !== 11) return;
  sendingCode.value = true;
  try {
    await authApi.sendSmsCode(phone.value);
    // 倒计时
    cooldown.value = 60;
    if (timer) clearInterval(timer);
    timer = setInterval(() => {
      cooldown.value--;
      if (cooldown.value <= 0) {
        if (timer) clearInterval(timer);
      }
    }, 1000);
  } catch {
    // 错误由拦截器 toast 处理
  } finally {
    sendingCode.value = false;
  }
}

async function doLogin() {
  if (!canLogin.value || loggingIn.value) return;
  loggingIn.value = true;
  try {
    const res = await authApi.login(phone.value, code.value);
    userStore.setToken(res.accessToken, res.refreshToken);
    router.push('/');
  } catch {
    // 错误由拦截器 toast 处理
  } finally {
    loggingIn.value = false;
  }
}
</script>

<style scoped>
.login-page {
  min-height: 100vh;
  display: flex;
  flex-direction: column;
  padding: 60px 24px 40px;
  background: var(--card);
}
.login-header {
  text-align: center;
  margin-bottom: 48px;
}
.logo-area {
  font-size: 48px;
  margin-bottom: 12px;
}
.title {
  font-size: 28px;
  font-weight: 700;
  color: var(--ink);
}
.subtitle {
  font-size: 14px;
  color: var(--ink-3);
  margin-top: 6px;
}
.login-form {
  display: flex;
  flex-direction: column;
  gap: 24px;
}
.field {
  display: flex;
  flex-direction: column;
  gap: 6px;
}
.field label {
  font-size: 13px;
  font-weight: 500;
  color: var(--ink-2);
}
.input {
  height: 48px;
  padding: 0 16px;
  background: var(--bg);
  border-radius: var(--radius-sm);
  font-size: 16px;
  border: 1px solid var(--line);
  transition: border-color 0.2s;
}
.input:focus {
  border-color: var(--brand);
}
.code-row {
  display: flex;
  gap: 10px;
}
.code-row .input {
  flex: 1;
}
.code-btn {
  flex: none;
  width: 110px;
  height: 48px;
  border-radius: var(--radius-sm);
  background: var(--brand-soft);
  color: var(--brand);
  font-size: 13px;
  font-weight: 500;
  border: 1px solid var(--brand);
}
.code-btn:disabled {
  opacity: 0.5;
}
.login-btn {
  margin-top: 16px;
  height: 50px;
  border-radius: 999px;
  background: var(--brand);
  color: #fff;
  font-size: 17px;
  font-weight: 600;
}
.login-btn:disabled {
  opacity: 0.4;
}
</style>