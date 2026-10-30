<template>
  <div class="login-page">
    <div class="login-card">
      <h2 style="margin-bottom:8px">商家登录</h2>
      <p style="color:#999;margin-bottom:24px;font-size:14px">智购 · 商家管理中心</p>

      <el-form :model="form" label-width="0">
        <el-form-item>
          <el-input v-model="form.phone" placeholder="手机号" size="large" maxlength="11" />
        </el-form-item>
        <el-form-item>
          <div style="display:flex;gap:8px;width:100%">
            <el-input v-model="form.code" placeholder="验证码" size="large" maxlength="6" @keyup.enter="login" />
            <el-button style="width:120px" :disabled="counting > 0" @click="sendCode">
              {{ counting > 0 ? `${counting}s` : '获取验证码' }}
            </el-button>
          </div>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" size="large" style="width:100%" :loading="loading" @click="login">
            登录
          </el-button>
        </el-form-item>
      </el-form>

      <div style="text-align:center;color:#999;font-size:12px">
        演示商家账号：13900000001（验证码由短信服务发放，开发环境从 Redis 读取）<br>
        消费者账号（13800138000）无法登录商家后台
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onUnmounted } from 'vue';
import { useRouter } from 'vue-router';
import { ElMessage } from 'element-plus';
import http from '@/api/request';
import { useUserStore } from '@/stores/user';

const router = useRouter();
const userStore = useUserStore();
const loading = ref(false);
const counting = ref(0);
let timer: ReturnType<typeof setInterval> | null = null;

const form = ref({ phone: '', code: '' });

async function sendCode() {
  if (!/^1\d{10}$/.test(form.value.phone)) {
    ElMessage.warning('请输入正确的手机号');
    return;
  }
  try {
    await http.post('/auth/send-sms-code', { phone: form.value.phone });
    ElMessage.success('验证码已发送');
    counting.value = 60;
    timer = setInterval(() => {
      counting.value -= 1;
      if (counting.value <= 0 && timer) {
        clearInterval(timer);
        timer = null;
      }
    }, 1000);
  } catch {
    ElMessage.error('发送失败，请稍后再试');
  }
}

async function login() {
  if (!/^1\d{10}$/.test(form.value.phone)) {
    ElMessage.warning('请输入正确的手机号');
    return;
  }
  if (!form.value.code) {
    ElMessage.warning('请输入验证码');
    return;
  }
  loading.value = true;
  try {
    const res = await http.post('/auth/login', { phone: form.value.phone, code: form.value.code });
    const token = res.data?.data?.accessToken;
    if (!token) {
      ElMessage.error('登录失败：未获取到凭证');
      return;
    }
    // 商家后台只允许商家角色账号登录（RBAC：与消费者账号体系分离）
    const role = res.data?.data?.role;
    if (role !== 'MERCHANT') {
      ElMessage.error('该账号不是商家账号，无法登录商家后台');
      return;
    }
    userStore.setToken(token);
    ElMessage.success('登录成功');
    router.push('/dashboard');
  } catch {
    ElMessage.error('验证码错误或已过期');
  } finally {
    loading.value = false;
  }
}

onUnmounted(() => {
  if (timer) clearInterval(timer);
});
</script>

<style scoped>
.login-page {
  min-height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  background: #f0f2f5;
}
.login-card {
  width: 380px;
  padding: 40px;
  background: #fff;
  border-radius: 12px;
  box-shadow: 0 4px 24px rgba(0,0,0,.08);
}
</style>
