<template>
  <el-container style="min-height:100vh">
    <!-- 侧边栏 -->
    <el-aside width="200px" style="background:#1a1a2e">
      <div style="padding:20px;color:#fff;font-size:18px;font-weight:700;text-align:center">
        智购 · 商家中心
      </div>
      <el-menu
        :default-active="route.path"
        router
        background-color="#1a1a2e"
        text-color="#rgba(255,255,255,.65)"
        active-text-color="#fff"
      >
        <el-menu-item index="/dashboard">
          <el-icon><DataAnalysis /></el-icon>
          <span>数据看板</span>
        </el-menu-item>
        <el-menu-item index="/product">
          <el-icon><Goods /></el-icon>
          <span>商品管理</span>
        </el-menu-item>
        <el-menu-item index="/order">
          <el-icon><List /></el-icon>
          <span>订单管理</span>
        </el-menu-item>
        <el-menu-item index="/aftersale">
          <el-icon><WarningFilled /></el-icon>
          <span>售后审核</span>
        </el-menu-item>
      </el-menu>
    </el-aside>

    <el-container>
      <!-- 顶栏 -->
      <el-header style="display:flex;align-items:center;justify-content:flex-end;background:#fff;border-bottom:1px solid #eee;padding:0 24px">
        <span style="margin-right:16px;color:#666">管理员</span>
        <el-button size="small" @click="logout">退出</el-button>
      </el-header>

      <!-- 内容区 -->
      <el-main style="background:#f5f6fa">
        <router-view />
      </el-main>
    </el-container>
  </el-container>
</template>

<script setup lang="ts">
import { useRoute, useRouter } from 'vue-router';
import { DataAnalysis, Goods, List, WarningFilled } from '@element-plus/icons-vue';
import { useUserStore } from '@/stores/user';

const route = useRoute();
const router = useRouter();
const userStore = useUserStore();

function logout() {
  userStore.clearToken();
  router.push('/login');
}
</script>