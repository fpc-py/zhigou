<script setup lang="ts">
import { computed } from 'vue';
import { useRoute } from 'vue-router';
import TabBar from '@/components/TabBar.vue';

const route = useRoute();
const showTabBar = computed(() => route.meta.tabbar !== false);
</script>

<template>
  <div class="app-shell">
    <RouterView />
    <TabBar v-if="showTabBar" />
  </div>
</template>

<style>
/* 全局样式已从 main.ts 导入 */
</style>
