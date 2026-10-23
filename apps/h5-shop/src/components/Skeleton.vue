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
  background: linear-gradient(90deg, #F0F1F8 25%, #E6E8F4 50%, #F0F1F8 75%);
  background-size: 400px 100%;
  animation: zf-shimmer 1.4s linear infinite;
  margin-bottom: 8px;
  border-radius: var(--radius-sm, 12px);
}
</style>
