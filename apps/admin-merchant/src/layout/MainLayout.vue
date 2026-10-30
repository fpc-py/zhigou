<template>
  <el-container style="min-height:100vh">
    <!-- 侧边栏（品牌深蓝渐变 + 高对比白色文字） -->
    <el-aside width="210px" class="side">
      <div class="brand">
        <div class="brand-logo">
          <el-icon :size="20"><DataAnalysis /></el-icon>
          <span>智购 · 商家中心</span>
        </div>
        <div class="brand-sub">MERCHANT CONSOLE</div>
      </div>
      <el-menu
        :default-active="route.path"
        router
        class="side-menu"
        background-color="transparent"
        text-color="rgba(255,255,255,0.82)"
        active-text-color="#fff"
      >
        <el-menu-item index="/dashboard">
          <el-icon><DataAnalysis /></el-icon>
          <span>数据看板</span>
        </el-menu-item>
        <el-menu-item index="/alert">
          <el-icon><Warning /></el-icon>
          <span>经营预警</span>
        </el-menu-item>
        <el-menu-item index="/fulfillment">
          <el-icon><Van /></el-icon>
          <span>履约异常</span>
        </el-menu-item>
        <el-menu-item index="/supply">
          <el-icon><TakeawayBox /></el-icon>
          <span>补货中心</span>
        </el-menu-item>
        <el-menu-item index="/brain">
          <el-icon><DataBoard /></el-icon>
          <span>经营大脑</span>
        </el-menu-item>
        <el-menu-item index="/review">
          <el-icon><ChatDotRound /></el-icon>
          <span>评论管理</span>
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
      <div class="side-foot">智购 v0.2.89 · 商家后台</div>
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
import { DataAnalysis, Goods, List, WarningFilled, Warning, Van, TakeawayBox, DataBoard, ChatDotRound } from '@element-plus/icons-vue';
import { useUserStore } from '@/stores/user';

const route = useRoute();
const router = useRouter();
const userStore = useUserStore();

function logout() {
  userStore.clearToken();
  router.push('/login');
}
</script>

<style scoped>
.side {
  display: flex;
  flex-direction: column;
  background: linear-gradient(180deg, #1f2d4d 0%, #182241 55%, #111a33 100%);
  border-right: 1px solid rgba(255, 255, 255, 0.06);
}

.brand {
  padding: 18px 16px 14px;
  border-bottom: 1px solid rgba(255, 255, 255, 0.08);
}

.brand-logo {
  display: flex;
  align-items: center;
  gap: 8px;
  color: #fff;
  font-size: 17px;
  font-weight: 700;
  letter-spacing: 0.5px;
}

.brand-logo .el-icon {
  color: #6a7bff;
  filter: drop-shadow(0 0 6px rgba(106, 123, 255, 0.6));
}

.brand-sub {
  margin-top: 6px;
  padding-left: 28px;
  color: rgba(255, 255, 255, 0.38);
  font-size: 10px;
  letter-spacing: 2px;
}

.side-menu {
  flex: 1;
  border-right: none !important;
  padding: 10px 8px;
}

.side-menu :deep(.el-menu-item) {
  height: 44px;
  line-height: 44px;
  margin: 2px 0;
  border-radius: 8px;
  font-size: 14px;
  position: relative;
  transition: background 0.25s, color 0.25s;
}

.side-menu :deep(.el-menu-item:hover) {
  background: rgba(255, 255, 255, 0.08);
  color: #fff;
}

.side-menu :deep(.el-menu-item.is-active) {
  background: linear-gradient(90deg, rgba(106, 123, 255, 0.32) 0%, rgba(106, 123, 255, 0.12) 100%);
  color: #fff;
  font-weight: 600;
}

.side-menu :deep(.el-menu-item.is-active::before) {
  content: '';
  position: absolute;
  left: 0;
  top: 12px;
  bottom: 12px;
  width: 3px;
  border-radius: 2px;
  background: linear-gradient(180deg, #6a7bff 0%, #00d4ff 100%);
  box-shadow: 0 0 8px rgba(106, 123, 255, 0.8);
}

.side-menu :deep(.el-menu-item .el-icon) {
  font-size: 17px;
}

.side-foot {
  padding: 12px 16px;
  color: rgba(255, 255, 255, 0.28);
  font-size: 11px;
  text-align: center;
  border-top: 1px solid rgba(255, 255, 255, 0.06);
}
</style>