<template>
  <div class="login-page">
    <div class="login-card">
      <h2 style="margin-bottom:8px">商家登录</h2>
      <p style="color:#999;margin-bottom:24px;font-size:14px">智购 · 商家管理中心</p>

      <el-form :model="form" label-width="0">
        <el-form-item>
          <el-input v-model="form.username" placeholder="账号" size="large" />
        </el-form-item>
        <el-form-item>
          <el-input v-model="form.password" type="password" placeholder="密码" size="large" show-password @keyup.enter="login" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" size="large" style="width:100%" :loading="loading" @click="login">
            登录
          </el-button>
        </el-form-item>
      </el-form>

      <div style="text-align:center;color:#999;font-size:12px">
        W11 演示账号: admin / admin123
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue';
import { useRouter } from 'vue-router';
import { ElMessage } from 'element-plus';
import { useUserStore } from '@/stores/user';

const router = useRouter();
const userStore = useUserStore();
const loading = ref(false);

const form = ref({ username: '', password: '' });

async function login() {
  if (!form.value.username || !form.value.password) {
    ElMessage.warning('请输入账号和密码');
    return;
  }
  loading.value = true;
  try {
    // W11 先写死 admin/admin123
    if (form.value.username === 'admin' && form.value.password === 'admin123') {
      userStore.setToken('mock-admin-token-' + Date.now());
      ElMessage.success('登录成功');
      router.push('/dashboard');
    } else {
      ElMessage.error('账号或密码错误');
    }
  } finally {
    loading.value = false;
  }
}
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