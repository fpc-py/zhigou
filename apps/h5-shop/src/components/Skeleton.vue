<template>
  <div class="skeleton-wrapper" :style="{ gap: gap + 'px' }">
    <div
      v-for="i in repeat"
      :key="i"
      class="skeleton-item"
      :style="{ width: w, height: h, borderRadius: radius }"
    />
  </div>
</template>

<script setup lang="ts">
defineProps<{
  w?: string;
  h?: string;
  repeat?: number;
  radius?: string;
  gap?: number;
}>();
</script>

<style scoped>
.skeleton-wrapper {
  display: flex;
  flex-direction: column;
}
.skeleton-item {
  background: linear-gradient(90deg, #eee 25%, #f5f5f5 50%, #eee 75%);
  background-size: 200% 100%;
  animation: shimmer 1.5s infinite;
  margin-bottom: 8px;
}
@keyframes shimmer {
  0% { background-position: 200% 0; }
  100% { background-position: -200% 0; }
}
</style>