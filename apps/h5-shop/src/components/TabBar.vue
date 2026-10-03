<template>
  <nav class="tabbar">
    <button
      v-for="tab in tabs"
      :key="tab.path"
      :class="{ on: currentPath === tab.path }"
      @click="go(tab.path)"
    >
      <span class="tab-icon">{{ tab.icon }}</span>
      <span class="tab-label">{{ tab.label }}</span>
    </button>
  </nav>
</template>

<script setup lang="ts">
import { computed } from 'vue';
import { useRoute, useRouter } from 'vue-router';

const tabs = [
  { path: '/', icon: '🏠', label: '首页' },
  { path: '/chat', icon: '💬', label: 'AI' },
  { path: '/compare', icon: '🔍', label: '比价' },
  { path: '/profile', icon: '👤', label: '我的' },
];

const route = useRoute();
const router = useRouter();
const currentPath = computed(() => route.path);

function go(path: string) {
  router.push(path);
}
</script>

<style scoped>
.tabbar {
  /* 固定在视口底部（H5 底部导航惯例）。
     原实现为文档流元素，长列表页需滚动到底才能看到/点击。 */
  position: fixed;
  bottom: 0;
  left: 50%;
  transform: translateX(-50%);
  width: 100%;
  max-width: 414px;
  height: 58px;
  display: flex;
  border-top: 1px solid var(--line);
  background: rgba(255, 255, 255, 0.96);
  backdrop-filter: blur(10px);
  z-index: 100;
}
.tabbar button {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 2px;
  color: var(--ink-3);
  font-size: 10.5px;
  font-weight: 500;
  transition: 0.15s;
}
.tabbar button.on {
  color: var(--brand);
}
.tab-icon {
  font-size: 20px;
}
</style>