<template>
  <nav class="tabbar">
    <button
      v-for="tab in tabs"
      :key="tab.path"
      :class="{ on: currentPath === tab.path }"
      @click="go(tab.path)"
    >
      <Icon :name="tab.icon" size="lg" :class="{ on: currentPath === tab.path }" />
      <span class="tab-label">{{ tab.label }}</span>
    </button>
  </nav>
</template>

<script setup lang="ts">
import { computed } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import Icon from './Icon.vue';

/** 底部 5 Tab：对齐原型（首页 / AI / 衣橱 / 社区 / 我的） */
const tabs = [
  { path: '/', icon: 'home', label: '首页' },
  { path: '/chat', icon: 'ai', label: 'AI' },
  { path: '/closet', icon: 'closet', label: '衣橱' },
  { path: '/community', icon: 'comm', label: '社区' },
  { path: '/profile', icon: 'me', label: '我的' },
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
  position: fixed;
  bottom: 0;
  left: 50%;
  transform: translateX(-50%);
  width: 100%;
  max-width: 414px;
  height: 60px;
  display: flex;
  border-top: 1px solid var(--line);
  background: rgba(255, 255, 255, 0.96);
  backdrop-filter: blur(10px);
  z-index: 100;
  padding-bottom: env(safe-area-inset-bottom, 0);
}
.tabbar button {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 3px;
  color: var(--ink-3);
  font-size: 10.5px;
  font-weight: 500;
  transition: 0.15s;
  background: transparent;
  border: none;
}
.tabbar button.on {
  color: var(--brand);
}
.tab-label {
  line-height: 1;
}
</style>
