<script setup lang="ts">
import { computed } from 'vue';
import { useRoute } from 'vue-router';
import TabBar from '@/components/TabBar.vue';

const route = useRoute();
const showTabBar = computed(() => route.meta.tabbar !== false);
</script>

<template>
  <div class="app-shell">
    <!-- 路由转场（P5：fade + 轻微上滑，out-in 避免重叠） -->
    <RouterView v-slot="{ Component }">
      <Transition name="zf-page" mode="out-in">
        <component :is="Component" :key="$route.path" />
      </Transition>
    </RouterView>
    <TabBar v-if="showTabBar" />
  </div>
</template>

<style scoped>
/* 路由转场：仅 opacity/transform，≤300ms，尊重 prefers-reduced-motion（全局兜底） */
.zf-page-enter-active,
.zf-page-leave-active {
  transition: opacity 0.24s var(--ease-out), transform 0.24s var(--ease-out);
}
.zf-page-enter-from {
  opacity: 0;
  transform: translateY(10px);
}
.zf-page-leave-to {
  opacity: 0;
  transform: translateY(-6px);
}
</style>
