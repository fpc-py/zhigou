<template>
  <div class="skeleton-wrapper" :style="{ gap: (gap || 8) + 'px' }">
    <div
      v-for="i in (repeat || 5)"
      :key="i"
      class="skeleton-item"
      :style="{ width: w || '100%', height: h || '40px', borderRadius: radius || '8px' }"
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
.skeleton-item {
  background: linear-gradient(90deg, #eee 25%, #f5f5f5 50%, #eee 75%);
  background-size: 200% 100%;
  animation: shimmer 1.5s infinite;
}
@keyframes shimmer {
  0% { background-position: 200% 0; }
  100% { background-position: -200% 0; }
}
</style>